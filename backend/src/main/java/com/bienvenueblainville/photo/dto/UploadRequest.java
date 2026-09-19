package com.bienvenueblainville.photo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadRequest(
        @NotBlank String contentType,
        @Positive long contentLength
) {
}
