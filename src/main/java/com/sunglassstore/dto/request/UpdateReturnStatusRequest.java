package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.util.Map;

@Getter
@Setter
public class UpdateReturnStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String adminComments;

    private Map<Long, String> itemConditions;
}
