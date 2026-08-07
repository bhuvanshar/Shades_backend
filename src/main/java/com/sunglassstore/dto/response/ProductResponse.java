package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.ProductAttribute;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class ProductResponse {
    private Long productId;
    private String productName;
    private String productDescription;
    private String brand;
    private BigDecimal basePrice;
    private Boolean isActive;
    private java.time.Instant publishedAt;
    /**
     * Canonical answer to "does this product get a New badge", decided by NewProductPolicy on the
     * server. Clients must render this rather than recomputing an age from a timestamp: the badge
     * has to be identical on the home page, Shop, Collections, every listing and the product page,
     * and it cannot depend on the customer's system clock.
     */
    private Boolean isNew;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Set<CategorySummary> categories;
    private List<VariantSummary> variants;
    private List<ImageSummary> images;
    private Map<String, String> attributes;

    /**
     * @param isNew decided by NewProductPolicy. Required rather than defaulted so a new call site
     *              cannot quietly ship a response whose badge is always false.
     */
    public static ProductResponse fromEntity(Product product, boolean isNew) {
        ProductResponse response = new ProductResponse();
        response.setProductId(product.getProductId());
        response.setProductName(product.getProductName());
        response.setProductDescription(product.getProductDescription());
        response.setBrand(product.getBrand());
        response.setBasePrice(product.getBasePrice());
        response.setIsActive(product.getIsActive());
        response.setPublishedAt(product.getPublishedAt());
        response.setIsNew(isNew);
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        response.setCategories(product.getCategories().stream().map(category ->
                new CategorySummary(category.getCategoryId(), category.getCategoryName())).collect(Collectors.toSet()));
        response.setVariants(product.getVariants().stream().map(VariantSummary::fromEntity).toList());
        response.setImages(product.getImages().stream().map(ImageSummary::fromEntity).toList());
        response.setAttributes(product.getAttributes().stream().filter(attribute -> attribute.getVariant() == null).collect(Collectors.toMap(
                attribute -> attribute.getAttributeName(), attribute -> attribute.getAttributeValue(), (first, second) -> second)));
        return response;
    }

    public record CategorySummary(Long categoryId, String categoryName) {}

    public record VariantSummary(Long variantId, String sku, String variantName, String variantDescription,
                                 BigDecimal price, Integer quantityAvailable, Integer lowStockThreshold, Boolean isActive,
                                 Map<String, String> attributes) {
        public static VariantSummary fromEntity(com.sunglassstore.entity.ProductVariant variant) {
            return new VariantSummary(variant.getVariantId(), variant.getSku(), variant.getVariantName(),
                    variant.getVariantDescription(),
                    variant.getPrice(), variant.getQuantityAvailable(), variant.getLowStockThreshold(), variant.getIsActive(),
                    variant.getAttributes().stream().collect(Collectors.toMap(ProductAttribute::getAttributeName,
                            ProductAttribute::getAttributeValue, (first, second) -> second)));
        }
    }

    public record ImageSummary(Long imageId, String imageUrl, String altText, Integer displayOrder,
                               Boolean isPrimary, Long variantId) {
        public static ImageSummary fromEntity(com.sunglassstore.entity.ProductImage image) {
            return new ImageSummary(image.getImageId(), image.getImageUrl(), image.getAltText(),
                    image.getDisplayOrder(), image.getIsPrimary(), extractVariantId(image.getImageUrl()));
        }
        private static Long extractVariantId(String url) {
            if (url == null) return null;
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/variants/(\\d+)/").matcher(url);
            return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
        }
    }
}
