package com.sunglassstore.controller;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.dto.response.ProductResponse;
import com.sunglassstore.service.ProductService;
import com.sunglassstore.service.LocalImageStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final LocalImageStorageService imageStorageService;

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(Pageable pageable) {
        return ResponseEntity.ok(productService.getAllActiveProducts(pageable));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductById(productId));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(@RequestParam String keyword, Pageable pageable) {
        return ResponseEntity.ok(productService.searchProducts(keyword, pageable));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getByCategory(@PathVariable Long categoryId, Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> updateProduct(@PathVariable Long productId,
                                                  @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(productId, request));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<Page<ProductResponse>> getAllProductsAdmin(Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @PatchMapping("/{productId}/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse> setProductActive(@PathVariable Long productId,
                                                             @RequestParam boolean active) {
        return ResponseEntity.ok(productService.setProductActive(productId, active));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }

    // Variant endpoints
    @PostMapping("/{productId}/variants")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.VariantSummary> addVariant(@PathVariable Long productId,
                                                      @Valid @RequestBody CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addVariant(productId, request));
    }

    @PutMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.VariantSummary> updateVariant(@PathVariable Long productId,
                                                         @PathVariable Long variantId,
                                                         @Valid @RequestBody CreateVariantRequest request) {
        return ResponseEntity.ok(productService.updateVariant(productId, variantId, request));
    }

    @DeleteMapping("/{productId}/variants/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long productId, @PathVariable Long variantId) {
        productService.deleteVariant(productId, variantId);
        return ResponseEntity.noContent().build();
    }

    // Image endpoints
    @PostMapping(value = "/{productId}/images/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.ImageSummary> uploadImage(
            @PathVariable Long productId,
            @RequestParam(required = false) Long variantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "") String altText,
            @RequestParam(defaultValue = "0") Integer displayOrder,
            @RequestParam(defaultValue = "false") Boolean isPrimary) {
        ProductResponse product = productService.getProductById(productId);
        if (variantId != null && product.getVariants().stream().noneMatch(v -> variantId.equals(v.variantId()))) {
            throw new com.sunglassstore.exception.BadRequestException("Variant does not belong to this product");
        }
        String imageUrl = imageStorageService.store(productId, variantId, file);
        CreateImageRequest request = new CreateImageRequest();
        request.setImageUrl(imageUrl);
        request.setAltText(altText);
        request.setDisplayOrder(displayOrder);
        request.setIsPrimary(isPrimary);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(productService.addImage(productId, request));
        } catch (RuntimeException ex) {
            imageStorageService.delete(imageUrl);
            throw ex;
        }
    }

    @PostMapping("/{productId}/images")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ProductResponse.ImageSummary> addImage(@PathVariable Long productId,
                                                  @Valid @RequestBody CreateImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.addImage(productId, request));
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteImage(@PathVariable Long productId, @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
