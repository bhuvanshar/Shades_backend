package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRequest {

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;
}
