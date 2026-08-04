package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateOrderRequest;
import com.sunglassstore.dto.response.AdminOrderResponse;
import com.sunglassstore.entity.*;
import com.sunglassstore.entity.enums.*;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.InsufficientInventoryException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.CouponService;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.email.event.OrderCancelledEmailRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final RefundRepository refundRepository;
    private final CouponService couponService;
    private final ApplicationEventPublisher eventPublisher;

    private static final BigDecimal TAX_RATE = new BigDecimal("18.00"); // 18% GST
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal STANDARD_SHIPPING = new BigDecimal("49.00");

    @Override
    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {

        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Step 1: Load the authenticated user's active cart
        Cart cart = cartRepository.findByUserUserIdAndCartStatusForUpdate(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("No active cart found"));

        // Step 2: Confirm the cart is not empty
        List<CartItem> cartItems = cart.getItems();
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // Lock every variant in a stable order before reading prices or stock. This prevents
        // overselling, stale-price orders, and deadlocks between multi-item checkouts.
        List<CartItem> sortedCartItems = new ArrayList<>(cartItems);
        sortedCartItems.sort(Comparator.comparing(item -> item.getVariant().getVariantId()));
        Map<Long, ProductVariant> lockedVariants = new HashMap<>();
        for (CartItem item : sortedCartItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BadRequestException("Cart contains an invalid quantity");
            }
            Long variantId = item.getVariant().getVariantId();
            ProductVariant variant = productVariantRepository.findByIdForUpdate(variantId)
                    .orElseThrow(() -> new BadRequestException(
                            "Product variant no longer available: " + variantId));

            if (!Boolean.TRUE.equals(variant.getIsActive()) || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                throw new BadRequestException("Product is no longer available: " + variant.getProduct().getProductName());
            }

            if (variant.getQuantityAvailable() < item.getQuantity()) {
                throw new InsufficientInventoryException(
                        "Insufficient stock for " + variant.getProduct().getProductName()
                                + " (SKU: " + variant.getSku() + "). Available: "
                                + variant.getQuantityAvailable() + ", Requested: " + item.getQuantity());
            }
            lockedVariants.put(variantId, variant);
        }

        // Step 5: Validate shipping address belongs to the user
        Address shippingAddress = addressRepository.findByAddressIdAndUserUserId(
                        request.getShippingAddressId(), userId)
                .orElseThrow(() -> new BadRequestException("Shipping address not found or does not belong to you"));
        Address billingAddress = request.getBillingAddressId() == null
                ? shippingAddress
                : addressRepository.findByAddressIdAndUserUserId(request.getBillingAddressId(), userId)
                    .orElseThrow(() -> new BadRequestException("Billing address not found or does not belong to you"));

        // Step 6: Validate coupon when supplied
        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = couponRepository.findByCouponCodeIgnoreCaseForUpdate(request.getCouponCode().trim())
                    .orElseThrow(() -> new BadRequestException("Invalid coupon code"));
        }

        // Step 7: Calculate subtotal, discount, tax, shipping, total
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItemQuantity = 0;
        for (CartItem item : sortedCartItems) {
            ProductVariant variant = lockedVariants.get(item.getVariant().getVariantId());
            BigDecimal price = variant.getPrice();
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            totalItemQuantity += item.getQuantity();
        }

        if (coupon != null) {
            // Full validation via coupon service
            com.sunglassstore.dto.request.ValidateCouponRequest validateReq =
                    new com.sunglassstore.dto.request.ValidateCouponRequest();
            validateReq.setCouponCode(coupon.getCouponCode());
            couponService.validateCoupon(userId, validateReq);
            discountAmount = couponService.calculateDiscount(coupon, subtotal, totalItemQuantity);
        }

        BigDecimal taxableAmount = subtotal.subtract(discountAmount);
        BigDecimal taxAmount = taxableAmount.multiply(TAX_RATE)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal shippingAmount = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : STANDARD_SHIPPING;
        BigDecimal totalAmount = taxableAmount.add(taxAmount).add(shippingAmount);
        if (request.getExpectedTotalAmount().setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalAmount.setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw new BadRequestException("Your order total changed. Return to your bag, review the latest prices and offer, then try again.");
        }

        // Step 8: Create the order
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PLACED);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discountAmount);
        order.setTaxAmount(taxAmount);
        order.setShippingAmount(shippingAmount);
        order.setTotalAmount(totalAmount);
        order.setShippingAddress(shippingAddress);
        order.setBillingAddress(billingAddress);
        order.setPurchasedAt(LocalDateTime.now());
        if (coupon != null) {
            order.setCoupon(coupon);
        }

        // Step 10: Copy shipping address values into order snapshot columns
        order.setShippingName(shippingAddress.getRecipientName());
        order.setShippingPhone(shippingAddress.getPhoneNumber());
        order.setShippingAddressLine1(shippingAddress.getAddressLine1());
        order.setShippingAddressLine2(shippingAddress.getAddressLine2());
        order.setShippingCity(shippingAddress.getCity());
        order.setShippingState(shippingAddress.getState());
        order.setShippingPincode(shippingAddress.getPincode());
        order.setShippingCountry(shippingAddress.getCountry());

        // Step 9: Create order items with snapshot data
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : sortedCartItems) {
            ProductVariant variant = lockedVariants.get(cartItem.getVariant().getVariantId());
            Product product = variant.getProduct();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setVariant(variant);
            orderItem.setProductName(product.getProductName());
            orderItem.setSku(variant.getSku());
            orderItem.setUnitPrice(variant.getPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setLineTotal(variant.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
            orderItems.add(orderItem);
        }
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // Step 11 & 12: Deduct the inventory rows already locked above and write movements.
        for (CartItem cartItem : sortedCartItems) {
            ProductVariant lockedVariant = lockedVariants.get(cartItem.getVariant().getVariantId());
            lockedVariant.setQuantityAvailable(lockedVariant.getQuantityAvailable() - cartItem.getQuantity());
            productVariantRepository.save(lockedVariant);

            InventoryMovement movement = new InventoryMovement();
            movement.setVariant(lockedVariant);
            movement.setMovementType(MovementType.SALE);
            movement.setQuantityChange(-cartItem.getQuantity());
            movement.setReferenceId(savedOrder.getOrderId());
            movement.setNotes("Order #" + savedOrder.getOrderId());
            inventoryMovementRepository.save(movement);
        }

        // Step 13: Create order status history
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(savedOrder);
        history.setNewStatus(OrderStatus.PLACED.name());
        history.setNotes("Order placed");
        orderStatusHistoryRepository.save(history);

        // Record coupon usage
        if (coupon != null) {
            CouponUsage usage = new CouponUsage();
            usage.setCoupon(coupon);
            usage.setUser(cart.getUser());
            usage.setOrder(savedOrder);
            usage.setDiscountAmount(discountAmount);
            couponUsageRepository.save(usage);
        }

        // Step 14: Mark the cart as ordered
        cart.setCartStatus(CartStatus.ORDERED);
        cartRepository.save(cart);

        // Step 15: Create a new empty active cart
        Cart newCart = new Cart();
        newCart.setUser(cart.getUser());
        newCart.setCartStatus(CartStatus.ACTIVE);
        cartRepository.save(newCart);

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserUserIdOrderByPurchasedAtDesc(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getUserOrder(Long userId, Long orderId) {
        return orderRepository.findByOrderIdAndUserUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    @Transactional
    public Order cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByOrderIdAndUserUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PLACED &&
                order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled in status: " + order.getOrderStatus());
        }

        OrderStatus oldStatus = order.getOrderStatus();
        BigDecimal refundAmount = settlePaymentsForCancellation(order);
        order.setOrderStatus(OrderStatus.CANCELLED);

        restoreInventory(order);

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus.name());
        history.setNewStatus(OrderStatus.CANCELLED.name());
        history.setNotes("Order cancelled by customer");
        orderStatusHistoryRepository.save(history);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCancelledEmailRequested(order.getUser().getEmail(),
                order.getUser().getName(), order.getOrderId(), refundAmount));
        return saved;
    }

    private void restoreInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            ProductVariant lockedVariant = productVariantRepository.findByIdForUpdate(
                    item.getVariant().getVariantId())
                    .orElseThrow(() -> new BadRequestException("Variant not found during cancellation"));

            lockedVariant.setQuantityAvailable(lockedVariant.getQuantityAvailable() + item.getQuantity());
            productVariantRepository.save(lockedVariant);

            InventoryMovement movement = new InventoryMovement();
            movement.setVariant(lockedVariant);
            movement.setMovementType(MovementType.CANCELLATION);
            movement.setQuantityChange(item.getQuantity());
            movement.setReferenceId(order.getOrderId());
            movement.setNotes("Order #" + order.getOrderId() + " cancelled");
            inventoryMovementRepository.save(movement);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAllByOrderByPurchasedAtDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status, String note) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderStatus oldStatus = order.getOrderStatus();
        validateTransition(oldStatus, status);
        if (status == OrderStatus.CANCELLED) {
            BigDecimal refundAmount = settlePaymentsForCancellation(order);
            restoreInventory(order);
            eventPublisher.publishEvent(new OrderCancelledEmailRequested(order.getUser().getEmail(),
                    order.getUser().getName(), order.getOrderId(), refundAmount));
        }
        order.setOrderStatus(status);
        if (status == OrderStatus.DELIVERED) order.setDeliveredAt(LocalDateTime.now());

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setOldStatus(oldStatus.name());
        history.setNewStatus(status.name());
        history.setNotes(note);
        orderStatusHistoryRepository.save(history);

        return orderRepository.save(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getAllOrdersForAdmin(Pageable pageable) {
        return orderRepository.findAllByOrderByPurchasedAtDesc(pageable).map(this::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getUserOrdersForCustomer(Long userId, Pageable pageable) {
        return orderRepository.findByUserUserIdOrderByPurchasedAtDesc(userId, pageable)
                .map(this::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getOrderForAdmin(Long orderId) {
        return toAdminResponse(getOrderById(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminOrderResponse getUserOrderForCustomer(Long userId, Long orderId) {
        return toAdminResponse(getUserOrder(userId, orderId));
    }

    @Override
    @Transactional
    public AdminOrderResponse updateOrderStatusForAdmin(Long orderId, OrderStatus status, String note) {
        return toAdminResponse(updateOrderStatus(orderId, status, note));
    }

    private AdminOrderResponse toAdminResponse(Order order) {
        Long id = order.getOrderId();
        return AdminOrderResponse.fromEntity(order,
                paymentRepository.findByOrderOrderIdOrderByCreatedAtDesc(id),
                shipmentRepository.findByOrderOrderIdOrderByCreatedAtDesc(id),
                orderStatusHistoryRepository.findByOrderOrderIdOrderByChangedAtAsc(id));
    }

    private void validateTransition(OrderStatus current, OrderStatus next) {
        if (current == next) throw new BadRequestException("Order is already in status " + current);
        boolean valid = switch (current) {
            case PLACED -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> next == OrderStatus.SHIPPED;
            case SHIPPED -> next == OrderStatus.DELIVERED;
            case DELIVERED -> next == OrderStatus.RETURNED;
            case CANCELLED, RETURNED -> false;
        };
        if (!valid) throw new BadRequestException("Invalid order status transition: " + current + " to " + next);
    }

    private BigDecimal settlePaymentsForCancellation(Order order) {
        BigDecimal refunded = BigDecimal.ZERO;
        for (Payment paymentView : paymentRepository.findByOrderOrderIdOrderByCreatedAtDesc(order.getOrderId())) {
            Payment payment = paymentRepository.findByIdForUpdate(paymentView.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found during cancellation"));
            if (payment.getPaymentStatus() == PaymentStatus.PENDING
                    || payment.getPaymentStatus() == PaymentStatus.AUTHORIZED) {
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                continue;
            }
            if (payment.getPaymentStatus() != PaymentStatus.PAID
                    && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) continue;

            BigDecimal alreadyRefunded = refundRepository.sumRefundedByPaymentId(payment.getPaymentId());
            if (alreadyRefunded == null) alreadyRefunded = BigDecimal.ZERO;
            BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded).max(BigDecimal.ZERO);
            if (remaining.signum() == 0) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                continue;
            }
            Refund refund = new Refund();
            refund.setPayment(payment);
            refund.setRefundAmount(remaining);
            refund.setRefundStatus(RefundStatus.COMPLETED);
            refund.setReason("Order cancelled before fulfilment");
            refund.setProviderReference("CANCEL-" + order.getOrderId() + "-" + payment.getPaymentId());
            refund.setProcessedAt(LocalDateTime.now());
            refundRepository.save(refund);
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            refunded = refunded.add(remaining);
        }
        return refunded;
    }
}
