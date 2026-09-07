package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SortingItemTranslationMapper {
    List<SortingItemTranslation> findByItemId(@Param("itemId") Long itemId);

    void insert(
            @Param("itemId") Long itemId,
            @Param("languageCode") LanguageCode languageCode,
            @Param("name") String name,
            @Param("instruction") String instruction,
            @Param("location") String location
    );

    void deleteByItemId(@Param("itemId") Long itemId);
}
