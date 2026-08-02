package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequest {

    private String addressType = "SHIPPING";

    @NotBlank(message = "Recipient name is required")
    @Size(max = 255)
    private String recipientName;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 50)
    private String houseNumber;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100)
    private String state;

    @NotBlank(message = "Pincode is required")
    @Size(max = 20)
    private String pincode;

    @NotBlank(message = "Country is required")
    @Size(max = 100)
    private String country;

    private Boolean isDefault = false;
}
