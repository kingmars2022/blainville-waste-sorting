package com.bienvenueblainville.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record NoticeRequest(
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @NotBlank String titleFr,
        @NotBlank String titleEn,
        @NotBlank String titleZh,
        @NotBlank String bodyFr,
        @NotBlank String bodyEn,
        @NotBlank String bodyZh,
        String sourceUrl,
        boolean active
) {
}
