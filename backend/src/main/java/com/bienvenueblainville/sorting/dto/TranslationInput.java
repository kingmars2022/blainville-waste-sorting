package com.bienvenueblainville.sorting.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record TranslationInput(
        @NotBlank String name,
        @NotBlank String instruction,
        String location,
        String availability,
        List<String> examples
) {
}
