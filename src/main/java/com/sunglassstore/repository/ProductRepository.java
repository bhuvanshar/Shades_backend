package com.sunglassstore.repository;

import com.sunglassstore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.productName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.productDescription) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> search(String query, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.categoryId = :categoryId AND p.isActive = true")
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
}
