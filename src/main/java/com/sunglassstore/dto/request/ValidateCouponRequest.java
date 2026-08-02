package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ValidateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String couponCode;

    @NotNull(message = "Order amount is required")
    private BigDecimal orderAmount;
}
