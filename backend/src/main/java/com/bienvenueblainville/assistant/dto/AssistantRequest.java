package com.bienvenueblainville.assistant.dto;

import com.bienvenueblainville.common.LanguageCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AssistantRequest(
        // Capped well below anything a resident would type. An unbounded
        // question is an unbounded prompt, and the prompt is what costs money.
        @NotBlank @Size(max = 300) String question,
        @NotNull LanguageCode language
) {
}
