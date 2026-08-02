package com.sunglassstore.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateImageRequest {

    @NotBlank(message = "Image URL is required")
    @Size(max = 2048)
    private String imageUrl;

    @Size(max = 255)
    private String altText;

    private Integer displayOrder = 0;

    private Boolean isPrimary = false;
}
