package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SortingItemKeywordMapper {
    List<SortingItemKeyword> findByItemId(@Param("itemId") Long itemId);

    void insertBatch(
            @Param("itemId") Long itemId,
            @Param("languageCode") LanguageCode languageCode,
            @Param("keywords") List<String> keywords
    );

    void deleteByItemId(@Param("itemId") Long itemId);
}
