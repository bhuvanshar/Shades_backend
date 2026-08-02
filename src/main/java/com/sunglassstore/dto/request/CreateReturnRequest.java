package com.sunglassstore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateReturnRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotBlank(message = "Return reason is required")
    private String returnReason;

    private String customerComments;

    @NotEmpty(message = "At least one return item is required")
    @Valid
    private List<ReturnItemRequest> items;

    @Getter
    @Setter
    public static class ReturnItemRequest {

        @NotNull(message = "Order item ID is required")
        private Long orderItemId;

        @NotNull(message = "Quantity is required")
        @jakarta.validation.constraints.Min(value = 1)
        private Integer quantity;

        private String itemCondition;
        private String returnReason;
    }
}
