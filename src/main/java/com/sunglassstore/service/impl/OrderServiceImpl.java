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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final PaymentRepository paymentRepository;
    private final ShipmentRepository shipmentRepository;
    private final CouponService couponService;
    private final EntityManager entityManager;

    private static final BigDecimal TAX_RATE = new BigDecimal("18.00"); // 18% GST
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal STANDARD_SHIPPING = new BigDecimal("49.00");

    @Override
    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {

        // Step 1: Load the authenticated user's active cart
        Cart cart = cartRepository.findByUserUserIdAndCartStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException("No active cart found"));

        // Step 2: Confirm the cart is not empty
        List<CartItem> cartItems = cart.getItems();
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // Step 3 & 4: Recheck variant availability and current prices
        for (CartItem item : cartItems) {
            ProductVariant variant = productVariantRepository.findById(item.getVariant().getVariantId())
                    .orElseThrow(() -> new BadRequestException(
                            "Product variant no longer available: " + item.getVariant().getVariantId()));

            if (!Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
                throw new BadRequestException("Product is no longer available: " + variant.getProduct().getProductName());
            }

            if (variant.getQuantityAvailable() < item.getQuantity()) {
                throw new InsufficientInventoryException(
                        "Insufficient stock for " + variant.getProduct().getProductName()
                                + " (SKU: " + variant.getSku() + "). Available: "
                                + variant.getQuantityAvailable() + ", Requested: " + item.getQuantity());
            }
        }

        // Step 5: Validate shipping address belongs to the user
        Address shippingAddress = addressRepository.findByAddressIdAndUserUserId(
                        request.getShippingAddressId(), userId)
                .orElseThrow(() -> new BadRequestException("Shipping address not found or does not belong to you"));

        // Step 6: Validate coupon when supplied
        Coupon coupon = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = couponRepository.findByCouponCodeIgnoreCase(request.getCouponCode().trim())
                    .orElseThrow(() -> new BadRequestException("Invalid coupon code"));
        }

        // Step 7: Calculate subtotal, discount, tax, shipping, total
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItemQuantity = 0;
        for (CartItem item : cartItems) {
            ProductVariant variant = item.getVariant();
            BigDecimal price = variant.getPrice();
            subtotal = subtotal.add(price.multiply(BigDecimal.valueOf(item.getQuantity())));
            totalItemQuantity += item.getQuantity();
        }

        if (coupon != null) {
            // Full validation via coupon service
            com.sunglassstore.dto.request.ValidateCouponRequest validateReq =
                    new com.sunglassstore.dto.request.ValidateCouponRequest();
            validateReq.setCouponCode(coupon.getCouponCode());
            validateReq.setOrderAmount(subtotal);
            validateReq.setItemQuantity(totalItemQuantity);
            couponService.validateCoupon(userId, validateReq);
            discountAmount = couponService.calculateDiscount(coupon, subtotal, totalItemQuantity);
        }

        BigDecimal taxableAmount = subtotal.subtract(discountAmount);
        BigDecimal taxAmount = taxableAmount.multiply(TAX_RATE)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal shippingAmount = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO : STANDARD_SHIPPING;
        BigDecimal totalAmount = taxableAmount.add(taxAmount).add(shippingAmount);

        // Step 8: Create the order
        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PLACED);
        order.setSubtotalAmount(subtotal);
        order.setDiscountAmount(discountAmount);
        order.setTaxAmount(taxAmount);
        order.setShippingAmount(shippingAmount);
        order.setTotalAmount(totalAmount);
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
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = cartItem.getVariant();
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

        // Step 11 & 12: Deduct inventory with pessimistic lock and write movements
        for (CartItem cartItem : cartItems) {
            ProductVariant lockedVariant = productVariantRepository.findByIdForUpdate(
                    cartItem.getVariant().getVariantId())
                    .orElseThrow(() -> new BadRequestException("Variant not found during inventory deduction"));

            if (lockedVariant.getQuantityAvailable() < cartItem.getQuantity()) {
                throw new InsufficientInventoryException(
                        "Insufficient stock for SKU: " + lockedVariant.getSku()
                                + " during final deduction. Available: " + lockedVariant.getQuantityAvailable());
            }

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
        Order order = orderRepository.findByOrderIdAndUserUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PLACED &&
                order.getOrderStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order cannot be cancelled in status: " + order.getOrderStatus());
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        // Restore inventory
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

        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setNewStatus(OrderStatus.CANCELLED.name());
        history.setNotes("Order cancelled by customer");
        orderStatusHistoryRepository.save(history);

        return orderRepository.save(order);
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
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderStatus oldStatus = order.getOrderStatus();
        validateTransition(oldStatus, status);
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
        if (current == next) return;
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
}
