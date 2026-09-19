package com.bienvenueblainville.assistant.dto;

public record AssistantSource(
        Long itemId,
        String name,
        String destinationType,
        String binColor,
        String sourceUrl,
        double score
) {
}
