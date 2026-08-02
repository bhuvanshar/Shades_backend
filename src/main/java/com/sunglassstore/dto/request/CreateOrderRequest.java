package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderRequest {

    @NotNull(message = "Shipping address ID is required")
    private Long shippingAddressId;

    private Long billingAddressId;

    private String couponCode;
}
