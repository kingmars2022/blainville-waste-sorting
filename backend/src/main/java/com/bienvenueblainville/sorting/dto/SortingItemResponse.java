package com.bienvenueblainville.sorting.dto;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.sorting.DestinationType;

import java.util.List;

public record SortingItemResponse(
        Long id,
        DestinationType destinationType,
        BinColor binColor,
        String sourceUrl,
        TranslationView fr,
        TranslationView en,
        TranslationView zh,
        List<String> keywordsFr,
        List<String> keywordsEn,
        List<String> keywordsZh
) {
}
