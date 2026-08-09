package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateImageRequest;
import com.sunglassstore.dto.request.CreateProductRequest;
import com.sunglassstore.dto.request.CreateVariantRequest;
import com.sunglassstore.catalog.ProductSlugs;
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
import java.util.List;
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
     * Ceiling on images per product. Configurable because "how many photos is reasonable" is a
     * merchandising decision, not an engineering one; 10 is the documented default. Enforced on the
     * server, so a caller bypassing the admin UI cannot exceed it, and reported as a validation
     * message rather than by silently dropping the extra files.
     */
    @org.springframework.beans.factory.annotation.Value("${app.catalog.max-product-images:10}")
    private int maxImagesPerProduct;

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
        // The only place a slug is created. An admin-supplied one is validated and must be free;
        // otherwise it is derived from the name and made unique by retry.
        product.setSlug(request.getSlug() == null || request.getSlug().isBlank()
                ? uniqueSlugFor(request.getProductName())
                : validateRequestedSlug(request.getSlug(), null));

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
        // The slug deliberately does NOT follow the name. Renaming a product must not move its
        // public URL: every link a customer bookmarked or shared, and every search result, points
        // at the old one. Only an explicit, different slug in the request changes it — and then the
        // admin has chosen to break those links knowingly.
        if (request.getSlug() != null && !request.getSlug().isBlank()
                && !request.getSlug().equals(product.getSlug())) {
            product.setSlug(validateRequestedSlug(request.getSlug(), productId));
        }

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
        List<ProductImage> existing = imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId);

        if (existing.size() >= maxImagesPerProduct) {
            throw new BadRequestException("A product can have at most " + maxImagesPerProduct
                    + " images. Remove one before adding another.");
        }

        ProductVariant variant = resolveImageVariant(product, request.getVariantId());

        // The first image of a product becomes its primary whether or not the caller asked. Without
        // this, a product whose uploads all arrived with isPrimary=false has no primary at all, and
        // every listing thumbnail falls back to "whichever row came first" — which is exactly the
        // non-determinism the ordering rules are meant to remove.
        boolean primary = Boolean.TRUE.equals(request.getIsPrimary()) || existing.isEmpty();
        if (primary) clearPrimaryFlag(existing);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setVariant(variant);
        image.setImageUrl(request.getImageUrl());
        image.setAltText(request.getAltText());
        image.setDisplayOrder(request.getDisplayOrder() == null ? existing.size() : request.getDisplayOrder());
        image.setIsPrimary(primary);

        return ProductResponse.ImageSummary.fromEntity(imageRepository.saveAndFlush(image));
    }

    /**
     * Demotes whatever is currently primary, and FLUSHES before returning.
     *
     * The flush is load-bearing, not caution. UQ_PRODUCT_IMAGES_PRIMARY makes "primary for product
     * N" unique, and Hibernate orders inserts before updates within a flush — so promoting a new
     * image while the old one is still marked primary in the database violates the constraint and
     * the whole request 409s. Demoting first, in its own statement, is what makes the swap legal.
     */
    private void clearPrimaryFlag(List<ProductImage> images) {
        boolean changed = false;
        for (ProductImage existing : images) {
            if (Boolean.TRUE.equals(existing.getIsPrimary())) {
                existing.setIsPrimary(false);
                imageRepository.save(existing);
                changed = true;
            }
        }
        if (changed) imageRepository.flush();
    }

    /**
     * The variant an image belongs to, or null for a general product photo.
     *
     * Rejects a variant belonging to a different product. The foreign key alone would not catch
     * that — it only proves the variant exists — so the cross-product check has to be explicit.
     */
    private ProductVariant resolveImageVariant(Product product, Long variantId) {
        if (variantId == null) return null;
        return product.getVariants().stream()
                .filter(candidate -> variantId.equals(candidate.getVariantId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Variant does not belong to this product"));
    }

    @Override
    @Transactional
    public List<ProductResponse.ImageSummary> reorderImages(Long productId, List<Long> imageIdsInOrder) {
        Product product = findProduct(productId);
        List<ProductImage> images = imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId);

        // The submitted list must be exactly this product's images. A partial list would leave the
        // unlisted ones at stale positions, and an id from another product would silently reorder
        // someone else's gallery — so both are refused rather than tolerated.
        Set<Long> owned = images.stream().map(ProductImage::getImageId).collect(java.util.stream.Collectors.toSet());
        Set<Long> submitted = new java.util.LinkedHashSet<>(imageIdsInOrder == null ? List.of() : imageIdsInOrder);
        if (submitted.size() != imageIdsInOrder.size()) {
            throw new BadRequestException("The image order contains the same image more than once");
        }
        if (!owned.equals(submitted)) {
            throw new BadRequestException("The image order must list exactly this product's images");
        }

        Map<Long, ProductImage> byId = images.stream()
                .collect(java.util.stream.Collectors.toMap(ProductImage::getImageId, image -> image));
        int position = 0;
        for (Long imageId : imageIdsInOrder) byId.get(imageId).setDisplayOrder(position++);
        imageRepository.saveAll(images);
        imageRepository.flush();
        return reloadImages(product.getProductId());
    }

    @Override
    @Transactional
    public List<ProductResponse.ImageSummary> setPrimaryImage(Long productId, Long imageId) {
        findProduct(productId);
        List<ProductImage> images = imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId);
        ProductImage target = images.stream().filter(image -> image.getImageId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        clearPrimaryFlag(images);
        target.setIsPrimary(true);
        imageRepository.saveAndFlush(target);
        return reloadImages(productId);
    }

    @Override
    @Transactional
    public ProductResponse.ImageSummary updateImage(Long productId, Long imageId, CreateImageRequest request) {
        Product product = findProduct(productId);
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (!image.getProduct().getProductId().equals(productId)) {
            // Same message and status as a missing image: an admin editing product A must not be
            // able to learn which image ids belong to product B by probing.
            throw new ResourceNotFoundException("Image not found");
        }
        // PATCH semantics: an absent field means "leave it alone", not "set it to null". Assigning
        // the variant unconditionally meant a request carrying only altText — which is exactly what
        // the admin UI sends when captioning a photo — silently detached that photo from its
        // colourway. The caller opts into clearing it by sending variantId: 0.
        if (request.getAltText() != null) image.setAltText(request.getAltText());
        if (request.getVariantId() != null) {
            image.setVariant(request.getVariantId() == 0 ? null : resolveImageVariant(product, request.getVariantId()));
        }
        return ProductResponse.ImageSummary.fromEntity(imageRepository.saveAndFlush(image));
    }

    private List<ProductResponse.ImageSummary> reloadImages(Long productId) {
        return imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId).stream()
                .sorted(java.util.Comparator
                        .comparing((ProductImage image) -> Boolean.TRUE.equals(image.getIsPrimary()) ? 0 : 1)
                        .thenComparing(ProductImage::getDisplayOrder)
                        .thenComparing(ProductImage::getImageId))
                .map(ProductResponse.ImageSummary::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (!image.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Image does not belong to this product");
        }
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        imageRepository.delete(image);
        imageRepository.flush();

        // Removing the primary must not leave the product without one, or every listing thumbnail
        // for it falls back to arbitrary order. The next image in gallery order is promoted; a
        // product left with no images has nothing to promote and legitimately ends up with none.
        if (wasPrimary) {
            imageRepository.findByProductProductIdOrderByDisplayOrderAscImageIdAsc(productId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsPrimary(true);
                        imageRepository.saveAndFlush(next);
                    });
        }

        // Storage last, and only after the row is gone. The reverse order would delete the file and
        // then leave a row pointing at nothing if the flush failed.
        imageStorageService.delete(image.getImageUrl());
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    /**
     * A free slug derived from the product name.
     *
     * The retry handles the ordinary case: a second "Classic Aviator" finds the plain slug taken
     * and takes a suffixed one instead.
     *
     * It cannot close the race. Two admins creating that name simultaneously both see the slug free
     * here, and the loser's INSERT violates UQ_PRODUCTS_SLUG. That is deliberate rather than
     * overlooked — the constraint is the authority, and GlobalExceptionHandler already maps the
     * violation to 409 "A record with this value already exists", which is an honest answer to a
     * genuine collision. Catching it here to retry would not work anyway: the violation surfaces on
     * flush, by which point this transaction is already rollback-only.
     */
    private String uniqueSlugFor(String productName) {
        String candidate = ProductSlugs.generate(productName);
        for (int attempt = 0; attempt < 5 && productRepository.existsBySlug(candidate); attempt++) {
            candidate = ProductSlugs.withFreshSuffix(candidate);
        }
        return candidate;
    }

    /**
     * An admin-supplied slug, or a 400/409 explaining exactly which rule it broke.
     *
     * @param productId the product being edited, so re-submitting a product's own slug is not a
     *                  conflict with itself; null when creating.
     */
    private String validateRequestedSlug(String requested, Long productId) {
        String slug = requested.trim().toLowerCase(java.util.Locale.ROOT);
        if (ProductSlugs.isReserved(slug)) {
            throw new BadRequestException("\"" + slug + "\" is a reserved word and cannot be used as a product URL");
        }
        if (ProductSlugs.isNumericId(slug)) {
            // Would be indistinguishable from a legacy /product/{id} link.
            throw new BadRequestException("A product URL cannot be only digits");
        }
        if (!ProductSlugs.isValid(slug)) {
            throw new BadRequestException("A product URL may use only lowercase letters, numbers and single hyphens, "
                    + "up to " + ProductSlugs.MAX_LENGTH + " characters");
        }
        productRepository.findBySlug(slug).ifPresent(existing -> {
            if (!existing.getProductId().equals(productId)) {
                // Names the collision without leaking the other product's id.
                throw new ConflictException("Another product already uses the URL \"" + slug + "\"");
            }
        });
        return slug;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductBySlug(String slug) {
        // No "did you mean" and no distinction between "never existed" and "exists but is inactive":
        // both are a plain 404, so the response cannot be used to probe which slugs are real.
        Product product = productRepository.findBySlug(slug == null ? "" : slug.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public String findCanonicalSlug(Long productId) {
        return productRepository.findSlugByProductId(productId)
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
