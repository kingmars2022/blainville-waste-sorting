package com.bienvenueblainville.sorting;

import com.bienvenueblainville.common.LanguageCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SortingItemExampleMapper {
    List<SortingItemExample> findByItemId(@Param("itemId") Long itemId);

    /** All of them, for the public guide - one query rather than one per item. */
    List<SortingItemExample> findAll();

    void insertBatch(
            @Param("itemId") Long itemId,
            @Param("languageCode") LanguageCode languageCode,
            @Param("examples") List<String> examples
    );

    void deleteByItemId(@Param("itemId") Long itemId);
}
