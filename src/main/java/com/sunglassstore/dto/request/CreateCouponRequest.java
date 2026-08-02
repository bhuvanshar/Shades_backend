package com.sunglassstore.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class CreateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    @Size(max = 50)
    private String couponCode;

    @Size(max = 255)
    private String description;

    @NotBlank(message = "Discount type is required")
    @Pattern(regexp = "PERCENTAGE|FIXED|PAIR_FIXED", message = "Invalid discount type")
    private String discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be positive")
    private BigDecimal discountValue;

    @DecimalMin(value = "0.00")
    private BigDecimal minimumOrderAmount = BigDecimal.ZERO;

    private BigDecimal maximumDiscountAmount;

    private Integer usageLimit;
    private Integer usageLimitPerUser;

    @NotNull(message = "Valid from date is required")
    private LocalDateTime validFrom;

    @NotNull(message = "Valid to date is required")
    private LocalDateTime validTo;
}
