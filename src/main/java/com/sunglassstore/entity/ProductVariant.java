package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "PRODUCT_VARIANTS")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VARIANT_ID")
    private Long variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "SKU", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "VARIANT_NAME")
    private String variantName;

    /** Optional per-variant copy; when null the storefront falls back to the product description. */
    @Column(name = "VARIANT_DESCRIPTION", columnDefinition = "TEXT")
    private String variantDescription;

    @Column(name = "PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "QUANTITY_AVAILABLE", nullable = false)
    private Integer quantityAvailable = 0;

    @Column(name = "LOW_STOCK_THRESHOLD", nullable = false)
    private Integer lowStockThreshold = 5;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Batched for the same reason as Product's collections, and measured separately because it is a
     * second, nested N+1: VariantSummary.fromEntity reads this for every variant of every product
     * in a listing page. Batching Product's collections alone took a 200-product listing from 1,868
     * SELECTs to 361 — the residue was one query per variant, which this removes.
     */
    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true)
    @org.hibernate.annotations.BatchSize(size = 64)
    private List<ProductAttribute> attributes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
