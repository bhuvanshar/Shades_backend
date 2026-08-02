package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "ORDER_ITEMS")
@Getter
@Setter
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ITEM_ID")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "VARIANT_ID", nullable = false)
    private ProductVariant variant;

    @Column(name = "PRODUCT_NAME", nullable = false)
    private String productName;

    @Column(name = "SKU", nullable = false, length = 100)
    private String sku;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "TAX_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "DISCOUNT_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "LINE_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;
}
