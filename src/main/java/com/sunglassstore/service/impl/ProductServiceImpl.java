package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.dto.response.ProductResponse;
import com.sunglassstore.entity.*;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.*;
import com.sunglassstore.service.ProductService;
import com.sunglassstore.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Set<String> STOREFRONT_CATEGORIES = Set.of("Men", "Women", "Unisex", "Accessory");

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final ProductAttributeRepository attributeRepository;
    private final CategoryRepository categoryRepository;
    private final com.sunglassstore.service.LocalImageStorageService imageStorageService;
    private final InventoryService inventoryService;
    private final com.sunglassstore.catalog.NewProductPolicy newProductPolicy;

    /**
     * Every ProductResponse in the application is built here, so the New badge is decided in
     * exactly one place. ProductResponse.fromEntity takes the flag rather than computing it, which
     * is what stops a future call site from quietly shipping an always-false badge.
     */
    private ProductResponse toResponse(Product product) {
        return ProductResponse.fromEntity(product, newProductPolicy.isNew(product));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByIsActiveTrue(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toResponse);
    }

    /** Ceiling on how much of the ranking one call may ask for. */
    private static final int MAX_BEST_SELLERS = 50;

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.sunglassstore.dto.response.BestSellerResponse> getBestSellers(int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_BEST_SELLERS);
        java.util.List<com.sunglassstore.catalog.BestSellerRow> ranked =
                productRepository.findBestSellers(capped);
        if (ranked.isEmpty()) {
            return java.util.List.of();
        }
        // Two queries in total, not one per product: the aggregate decides the ranking and this
        // fetches exactly the products it named. findAllById returns them in no particular order,
        // so the ranking order is reapplied from `ranked` below rather than taken from this list.
        java.util.Map<Long, Product> byId = productRepository
                .findAllById(ranked.stream().map(com.sunglassstore.catalog.BestSellerRow::getProductId).toList())
                .stream().collect(java.util.stream.Collectors.toMap(Product::getProductId, product -> product));
        return ranked.stream()
                .filter(row -> byId.containsKey(row.getProductId()))
                .map(row -> new com.sunglassstore.dto.response.BestSellerResponse(
                        toResponse(byId.get(row.getProductId())),
                        row.getSoldQuantity() == null ? 0L : row.getSoldQuantity(),
                        row.getSoldRevenue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        return toResponse(findProduct(productId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() > 100) {
            throw new BadRequestException("Search keyword cannot exceed 100 characters");
        }
        return productRepository.search(normalized, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = new Product();
        product.setProductName(request.getProductName());
        product.setBrand(request.getBrand());
        product.setProductDescription(request.getProductDescription());
        product.setBasePrice(request.getBasePrice());

        // Set categories
        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            Set<Category> categories = new HashSet<>();
            for (Long catId : request.getCategoryIds()) {
                Category category = categoryRepository.findById(catId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + catId));
                validateStorefrontCategory(category);
                categories.add(category);
            }
            product.setCategories(categories);
        }

        Product saved = productRepository.save(product);

        if (request.getInitialVariant() != null) {
            CreateVariantRequest variantRequest = request.getInitialVariant();
            if (variantRepository.existsBySku(variantRequest.getSku())) {
                throw new ConflictException("SKU already exists: " + variantRequest.getSku());
            }
            ProductVariant variant = new ProductVariant();
            variant.setProduct(saved);
            variant.setSku(variantRequest.getSku());
            variant.setVariantName(variantRequest.getVariantName());
            variant.setVariantDescription(variantRequest.getVariantDescription());
            variant.setPrice(variantRequest.getPrice());
            int openingStock = variantRequest.getQuantityAvailable();
            variant.setQuantityAvailable(0);
            variant.setLowStockThreshold(variantRequest.getLowStockThreshold());
            setVariantAttributes(saved, variant, variantRequest.getAttributes());
            variantRepository.save(variant);
            if (openingStock > 0) inventoryService.adjustInventory(variant.getVariantId(), openingStock,
                    com.sunglassstore.entity.enums.MovementType.PURCHASE, "Opening stock from product creation");
            saved.getVariants().add(variant);
        }

        // Add attributes
        if (request.getAttributes() != null) {
            for (Map.Entry<String, String> entry : request.getAttributes().entrySet()) {
                ProductAttribute attr = new ProductAttribute();
                attr.setProduct(saved);
                attr.setAttributeName(entry.getKey());
                attr.setAttributeValue(entry.getValue());
                attributeRepository.save(attr);
            }
        }

        return toResponse(productRepository.findById(saved.getProductId()).orElse(saved));
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long productId, CreateProductRequest request) {
        Product product = findProduct(productId);
        product.setProductName(request.getProductName());
        product.setBrand(request.getBrand());
        product.setProductDescription(request.getProductDescription());
        product.setBasePrice(request.getBasePrice());

        if (request.getCategoryIds() != null) {
            Set<Category> categories = new HashSet<>();
            for (Long catId : request.getCategoryIds()) {
                Category category = categoryRepository.findById(catId)
                        .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + catId));
                validateStorefrontCategory(category);
                categories.add(category);
            }
            product.setCategories(categories);
        }

        if (request.getInitialVariant() != null) {
            CreateVariantRequest variantRequest = request.getInitialVariant();
            ProductVariant variant;
            if (variantRequest.getVariantId() == null) {
                if (variantRepository.existsBySku(variantRequest.getSku())) {
                    throw new ConflictException("SKU already exists: " + variantRequest.getSku());
                }
                variant = new ProductVariant();
                variant.setProduct(product);
                product.getVariants().add(variant);
            } else {
                variant = variantRepository.findById(variantRequest.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
                if (!variant.getProduct().getProductId().equals(productId)) {
                    throw new BadRequestException("Variant does not belong to this product");
                }
                if (!variant.getSku().equals(variantRequest.getSku())
                        && variantRepository.existsBySku(variantRequest.getSku())) {
                    throw new ConflictException("SKU already exists: " + variantRequest.getSku());
                }
            }
            variant.setSku(variantRequest.getSku());
            variant.setVariantName(variantRequest.getVariantName());
            variant.setVariantDescription(variantRequest.getVariantDescription());
            variant.setPrice(variantRequest.getPrice());
            int previousStock = variant.getQuantityAvailable();
            variant.setLowStockThreshold(variantRequest.getLowStockThreshold());
            setVariantAttributes(product, variant, variantRequest.getAttributes());
            variantRepository.save(variant);
            int stockChange = variantRequest.getQuantityAvailable() - previousStock;
            if (stockChange != 0) inventoryService.adjustInventory(variant.getVariantId(), stockChange,
                    com.sunglassstore.entity.enums.MovementType.ADJUSTMENT, "Stock updated from product editor");
        }

        // updateProduct never touches publishedAt: editing a name, price, description or stock
        // level must not make an old product New again.
        return toResponse(productRepository.save(product));
    }

    private void validateStorefrontCategory(Category category) {
        if (!Boolean.TRUE.equals(category.getIsActive()) || !STOREFRONT_CATEGORIES.contains(category.getCategoryName())) {
            throw new BadRequestException("Category must be Men, Women, Unisex, or Accessory");
        }
    }

    @Override
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = findProduct(productId);
        java.util.List<String> imageUrls = product.getImages().stream().map(ProductImage::getImageUrl).toList();
        try {
            productRepository.delete(product);
            productRepository.flush();
            imageUrls.forEach(imageStorageService::delete);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("This product has order or inventory history and cannot be permanently removed. Deactivate it instead.");
        }
    }

    @Override
    @Transactional
    public ProductResponse setProductActive(Long productId, boolean active) {
        Product product = findProduct(productId);
        product.setIsActive(active);
        // First activation is the publication event the New badge is measured from. publish() is
        // idempotent, so relisting a delisted product keeps its original date rather than making
        // an old product New again.
        if (active) {
            product.publish();
        }
        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse.VariantSummary addVariant(Long productId, CreateVariantRequest request) {
        Product product = findProduct(productId);

        if (variantRepository.existsBySku(request.getSku())) {
            throw new ConflictException("SKU already exists: " + request.getSku());
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(request.getSku());
        variant.setVariantName(request.getVariantName());
        variant.setVariantDescription(request.getVariantDescription());
        
        variant.setPrice(request.getPrice());
        int openingStock = request.getQuantityAvailable();
        variant.setQuantityAvailable(0);

        variant.setLowStockThreshold(request.getLowStockThreshold());
        setVariantAttributes(product, variant, request.getAttributes());
        ProductVariant saved = variantRepository.save(variant);
        if (openingStock > 0) inventoryService.adjustInventory(saved.getVariantId(), openingStock,
                com.sunglassstore.entity.enums.MovementType.PURCHASE, "Opening stock from variant creation");
        return ProductResponse.VariantSummary.fromEntity(saved);
    }

    @Override
    @Transactional
    public ProductResponse.VariantSummary updateVariant(Long productId, Long variantId, CreateVariantRequest request) {
        findProduct(productId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        if (!variant.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Variant does not belong to this product");
        }

        variant.setVariantName(request.getVariantName());
        variant.setVariantDescription(request.getVariantDescription());
        variant.setSku(request.getSku());
        variant.setPrice(request.getPrice());
        int previousStock = variant.getQuantityAvailable();
        variant.setLowStockThreshold(request.getLowStockThreshold());
        setVariantAttributes(variant.getProduct(), variant, request.getAttributes());

        ProductVariant saved = variantRepository.save(variant);
        int stockChange = request.getQuantityAvailable() - previousStock;
        if (stockChange != 0) inventoryService.adjustInventory(saved.getVariantId(), stockChange,
                com.sunglassstore.entity.enums.MovementType.ADJUSTMENT, "Stock updated from variant editor");
        return ProductResponse.VariantSummary.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteVariant(Long productId, Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));
        if (!variant.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Variant does not belong to this product");
        }
        variantRepository.delete(variant);
    }

    @Override
    @Transactional
    public ProductResponse.ImageSummary addImage(Long productId, CreateImageRequest request) {
        Product product = findProduct(productId);

        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            imageRepository.findByProductProductId(productId).forEach(existing -> {
                existing.setIsPrimary(false);
                imageRepository.save(existing);
            });
        }

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(request.getImageUrl());
        image.setAltText(request.getAltText());
        image.setDisplayOrder(request.getDisplayOrder());
        image.setIsPrimary(request.getIsPrimary());

        return ProductResponse.ImageSummary.fromEntity(imageRepository.save(image));
    }

    @Override
    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (!image.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Image does not belong to this product");
        }
        imageRepository.delete(image);
        imageRepository.flush();
        imageStorageService.delete(image.getImageUrl());
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void setVariantAttributes(Product product, ProductVariant variant, Map<String, String> attributes) {
        variant.getAttributes().clear();
        if (attributes == null) return;
        attributes.forEach((name, value) -> {
            if (value == null || value.isBlank()) return;
            ProductAttribute attribute = new ProductAttribute();
            attribute.setProduct(product);
            attribute.setVariant(variant);
            attribute.setAttributeName(name);
            attribute.setAttributeValue(value);
            variant.getAttributes().add(attribute);
        });
    }
}
