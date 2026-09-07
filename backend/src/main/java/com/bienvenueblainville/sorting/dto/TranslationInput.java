package com.bienvenueblainville.sorting.dto;

import jakarta.validation.constraints.NotBlank;

public record TranslationInput(@NotBlank String name, @NotBlank String instruction, String location) {
}
