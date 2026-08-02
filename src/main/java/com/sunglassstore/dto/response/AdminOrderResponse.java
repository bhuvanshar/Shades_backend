package com.sunglassstore.dto.response;

import com.sunglassstore.entity.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderResponse(
        Long orderId, String orderStatus, BigDecimal subtotalAmount, BigDecimal discountAmount,
        BigDecimal taxAmount, BigDecimal shippingAmount, BigDecimal totalAmount,
        LocalDateTime purchasedAt, LocalDateTime deliveredAt, LocalDateTime updatedAt,
        Customer customer, ShippingAddress shippingAddress, List<Item> items,
        List<PaymentInfo> payments, List<ShipmentInfo> shipments, List<History> history) {

    public static AdminOrderResponse fromEntity(Order order, List<Payment> payments,
                                                 List<Shipment> shipments, List<OrderStatusHistory> history) {
        User user = order.getUser();
        return new AdminOrderResponse(order.getOrderId(), order.getOrderStatus().name(),
                order.getSubtotalAmount(), order.getDiscountAmount(), order.getTaxAmount(),
                order.getShippingAmount(), order.getTotalAmount(), order.getPurchasedAt(),
                order.getDeliveredAt(), order.getUpdatedAt(),
                new Customer(user.getUserId(), user.getName(), user.getEmail(), user.getPhoneNumber()),
                new ShippingAddress(order.getShippingName(), order.getShippingPhone(),
                        order.getShippingAddressLine1(), order.getShippingAddressLine2(),
                        order.getShippingCity(), order.getShippingState(), order.getShippingPincode(),
                        order.getShippingCountry()),
                order.getItems().stream().map(i -> new Item(i.getOrderItemId(), i.getProductName(),
                        i.getSku(), i.getQuantity(), i.getUnitPrice(), i.getTaxAmount(),
                        i.getDiscountAmount(), i.getLineTotal())).toList(),
                payments.stream().map(p -> new PaymentInfo(p.getPaymentId(), p.getAmount(),
                        p.getPaymentMethod(), p.getPaymentStatus().name(), p.getPaymentProvider(),
                        p.getProviderReference(), p.getCreatedAt(), p.getPaidAt())).toList(),
                shipments.stream().map(s -> new ShipmentInfo(s.getShipmentId(), s.getShippingProvider(),
                        s.getTrackingNumber(), s.getShipmentStatus().name(), s.getShippedAt(),
                        s.getExpectedDeliveryAt(), s.getDeliveredAt())).toList(),
                history.stream().map(h -> new History(h.getOldStatus(), h.getNewStatus(),
                        h.getNotes(), h.getChangedAt())).toList());
    }

    public record Customer(Long userId, String name, String email, String phoneNumber) {}
    public record ShippingAddress(String name, String phone, String line1, String line2,
                                  String city, String state, String pincode, String country) {}
    public record Item(Long orderItemId, String productName, String sku, Integer quantity,
                       BigDecimal unitPrice, BigDecimal taxAmount, BigDecimal discountAmount,
                       BigDecimal lineTotal) {}
    public record PaymentInfo(Long paymentId, BigDecimal amount, String method, String status,
                              String provider, String reference, LocalDateTime createdAt,
                              LocalDateTime paidAt) {}
    public record ShipmentInfo(Long shipmentId, String provider, String trackingNumber, String status,
                               LocalDateTime shippedAt, LocalDateTime expectedDeliveryAt,
                               LocalDateTime deliveredAt) {}
    public record History(String oldStatus, String newStatus, String notes, LocalDateTime changedAt) {}
}
