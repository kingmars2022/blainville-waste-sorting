package com.bienvenueblainville.collection.dto;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.collection.CollectionType;
import com.bienvenueblainville.common.Sector;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CollectionEventRequest(
        @NotNull LocalDate collectionDate,
        @NotNull Sector sector,
        @NotNull CollectionType collectionType,
        @NotNull BinColor binColor,
        String noteFr,
        String noteEn,
        String noteZh,
        String sourceUrl
) {
}
