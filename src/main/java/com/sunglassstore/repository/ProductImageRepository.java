package com.sunglassstore.repository;

import com.sunglassstore.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    java.util.List<ProductImage> findByProductProductId(Long productId);
}
