package com.bienvenueblainville.sorting;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface SortingItemMapper {
    List<SortingItem> findAll();

    Optional<SortingItem> findById(@Param("id") Long id);

    void insert(Map<String, Object> params);

    void update(
            @Param("id") Long id,
            @Param("destinationType") DestinationType destinationType,
            @Param("binColor") com.bienvenueblainville.collection.BinColor binColor,
            @Param("sourceUrl") String sourceUrl
    );

    void delete(@Param("id") Long id);
}
