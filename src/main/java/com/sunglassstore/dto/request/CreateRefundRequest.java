package com.sunglassstore.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateRefundRequest {

    @NotNull(message = "Refund amount is required")
    @DecimalMin(value = "0.01", message = "Refund amount must be positive")
    private BigDecimal refundAmount;

    @NotNull(message = "Return ID is required")
    private Long returnId;

    @NotBlank(message = "Refund reason is required")
    @Size(max = 255, message = "Refund reason cannot exceed 255 characters")
    private String reason;
}
