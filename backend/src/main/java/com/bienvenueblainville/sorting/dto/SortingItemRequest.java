package com.bienvenueblainville.sorting.dto;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.sorting.DestinationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SortingItemRequest(
        @NotNull DestinationType destinationType,
        @NotNull BinColor binColor,
        String sourceUrl,
        @NotNull @Valid TranslationInput fr,
        @NotNull @Valid TranslationInput en,
        @NotNull @Valid TranslationInput zh,
        List<String> keywordsFr,
        List<String> keywordsEn,
        List<String> keywordsZh
) {
}
