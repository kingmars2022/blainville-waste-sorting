package com.bienvenueblainville.sorting;

import com.bienvenueblainville.collection.BinColor;

public record SortingItem(
        Long id,
        DestinationType destinationType,
        BinColor binColor,
        String sourceUrl
) {
}
