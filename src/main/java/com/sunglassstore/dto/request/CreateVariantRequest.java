package com.sunglassstore.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class CreateVariantRequest {

    private Long variantId;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    @Size(max = 255)
    private String variantName;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    @NotNull(message = "Quantity is required")
    @Min(value = 0)
    private Integer quantityAvailable;

    @Min(value = 0)
    private Integer lowStockThreshold = 5;

    /** Variant-level attributes like frame_color, lens_color */
    private Map<String, String> attributes;
}
