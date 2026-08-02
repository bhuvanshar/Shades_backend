package com.sunglassstore.dto.request;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    @Size(max = 255)
    private String productName;

    private String productDescription;

    @Size(max = 150)
    private String brand;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.00", message = "Price must be non-negative")
    private BigDecimal basePrice;

    private Long taxRateId;

    private List<Long> categoryIds;

    /** Key-value pairs for product-level attributes like frame_material, uv_protection etc. */
    private Map<String, String> attributes;

    /** Initial sellable variant and opening stock, used when a product is first created. */
    @Valid
    private CreateVariantRequest initialVariant;
}
