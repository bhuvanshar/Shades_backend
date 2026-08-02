package com.sunglassstore.entity;

import com.sunglassstore.entity.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ORDERS")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ID")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SHIPPING_ADDRESS_ID")
    private Address shippingAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BILLING_ADDRESS_ID")
    private Address billingAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COUPON_ID")
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "ORDER_STATUS", nullable = false, length = 30)
    private OrderStatus orderStatus = OrderStatus.PLACED;

    @Column(name = "SUBTOTAL_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "DISCOUNT_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "TAX_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "SHIPPING_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(name = "TOTAL_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Denormalized shipping address snapshot
    @Column(name = "SHIPPING_NAME", nullable = false)
    private String shippingName;

    @Column(name = "SHIPPING_PHONE", length = 20)
    private String shippingPhone;

    @Column(name = "SHIPPING_ADDRESS_LINE_1", nullable = false)
    private String shippingAddressLine1;

    @Column(name = "SHIPPING_ADDRESS_LINE_2")
    private String shippingAddressLine2;

    @Column(name = "SHIPPING_CITY", nullable = false, length = 100)
    private String shippingCity;

    @Column(name = "SHIPPING_STATE", nullable = false, length = 100)
    private String shippingState;

    @Column(name = "SHIPPING_PINCODE", nullable = false, length = 20)
    private String shippingPincode;

    @Column(name = "SHIPPING_COUNTRY", nullable = false, length = 100)
    private String shippingCountry;

    @Column(name = "PURCHASED_AT", nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "DELIVERED_AT")
    private LocalDateTime deliveredAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        purchasedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
