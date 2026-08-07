package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    Page<ProductResponse> getAllActiveProducts(Pageable pageable);
    Page<ProductResponse> getAllProducts(Pageable pageable);
    ProductResponse getProductById(Long productId);
    Page<ProductResponse> searchProducts(String keyword, Pageable pageable);
    Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable);

    /**
     * Public Best Sellers ranking, highest net sales first. See ProductRepository.findBestSellers
     * for the eligibility, refund and tie-breaking rules.
     */
    java.util.List<com.sunglassstore.dto.response.BestSellerResponse> getBestSellers(int limit);
    ProductResponse createProduct(CreateProductRequest request);
    ProductResponse updateProduct(Long productId, CreateProductRequest request);
    void deleteProduct(Long productId);
    ProductResponse setProductActive(Long productId, boolean active);
    ProductResponse.VariantSummary addVariant(Long productId, CreateVariantRequest request);
    ProductResponse.VariantSummary updateVariant(Long productId, Long variantId, CreateVariantRequest request);
    void deleteVariant(Long productId, Long variantId);
    ProductResponse.ImageSummary addImage(Long productId, CreateImageRequest request);
    void deleteImage(Long productId, Long imageId);
}
