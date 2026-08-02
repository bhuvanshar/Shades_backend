package com.sunglassstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PRODUCT_IMAGES")
@Getter
@Setter
@NoArgsConstructor
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMAGE_ID")
    private Long imageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private Product product;

    @Column(name = "IMAGE_URL", nullable = false, length = 2048)
    private String imageUrl;

    @Column(name = "ALT_TEXT")
    private String altText;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "IS_PRIMARY", nullable = false)
    private Boolean isPrimary = false;
}
