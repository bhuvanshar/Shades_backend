package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "PRODUCTS")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID")
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "TAX_RATE_ID")
    private TaxRate taxRate;

    @Column(name = "PRODUCT_NAME", nullable = false)
    private String productName;

    @Column(name = "PRODUCT_DESCRIPTION", columnDefinition = "TEXT")
    private String productDescription;

    @Column(name = "BRAND", length = 150)
    private String brand;

    @Column(name = "BASE_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean isActive = true;

    /**
     * When this product first became publicly available, in UTC. Null means it has never been
     * published, which is why a draft can never carry a New badge.
     *
     * Stamped once, on first activation, and never moved afterwards. Re-activating a delisted
     * product deliberately does NOT re-stamp it: the badge answers "is this new to the catalogue",
     * and a delist/relist cycle is not a new product. Nothing else may write this column — that is
     * the whole point of not measuring the badge from UPDATED_AT.
     */
    // Instant, not LocalDateTime. A LocalDateTime here round-trips through Connector/J with
    // serverTimezone=UTC and comes back shifted into the JVM's default zone, so comparing it
    // against LocalDateTime.now(UTC) moved the 30-day boundary by the server's offset — a product
    // published 30 days and 2 minutes ago still reported as New. An Instant has no such ambiguity:
    // it is the same point in time on both sides of the driver.
    @Column(name = "PUBLISHED_AT")
    private java.time.Instant publishedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    // @OrderBy is not cosmetic here. Every surface that has to pick ONE variant to display and sell
    // — the listing card, the product page's default selection — resolves "first in-stock variant"
    // against this list, and without an explicit order Hibernate returns whatever order the join
    // happens to produce. That made the default selection non-deterministic in principle even
    // though it usually came back in PK order. Ascending variantId is insertion order, which is
    // also the order the admin UI adds colourways in.
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("variantId ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttribute> attributes = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "PRODUCT_CATEGORIES",
            joinColumns = @JoinColumn(name = "PRODUCT_ID"),
            inverseJoinColumns = @JoinColumn(name = "CATEGORY_ID")
    )
    private Set<Category> categories = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        // A product created already active is published at that moment. One created as a draft
        // gets its stamp from publish() when an admin activates it.
        if (Boolean.TRUE.equals(isActive)) {
            publish();
        }
    }

    /**
     * Records first publication. Idempotent, so re-activation keeps the original date — see the
     * field comment. UTC because the New window is compared in UTC.
     */
    public void publish() {
        if (publishedAt == null) {
            publishedAt = java.time.Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
