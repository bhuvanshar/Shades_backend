package com.sunglassstore.repository;

import com.sunglassstore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByIsActiveTrue(Pageable pageable);

    @Query(value = "SELECT DISTINCT p FROM Product p " +
           "LEFT JOIN p.variants v ON v.isActive = true " +
           "LEFT JOIN p.categories c " +
           "LEFT JOIN p.attributes a " +
           "WHERE p.isActive = true AND " +
           "(LOWER(COALESCE(p.productName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(p.productDescription, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(v.variantName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(COALESCE(a.attributeValue, '')) LIKE LOWER(CONCAT('%', :query, '%')))",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p " +
                   "LEFT JOIN p.variants v ON v.isActive = true LEFT JOIN p.categories c LEFT JOIN p.attributes a " +
                   "WHERE p.isActive = true AND " +
                   "(LOWER(COALESCE(p.productName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(p.brand, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(p.productDescription, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(v.variantName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(v.sku, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(c.categoryName, '')) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                   "LOWER(COALESCE(a.attributeValue, '')) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> search(String query, Pageable pageable);

    @Query("SELECT p FROM Product p JOIN p.categories c WHERE c.categoryId = :categoryId AND p.isActive = true")
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
}
