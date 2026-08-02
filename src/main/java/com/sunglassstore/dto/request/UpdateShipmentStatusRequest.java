package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateShipmentStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;
}
