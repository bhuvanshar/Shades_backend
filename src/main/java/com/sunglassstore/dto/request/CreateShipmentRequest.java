package com.sunglassstore.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateShipmentRequest {

    private String shippingProvider;
    private String trackingNumber;
}
