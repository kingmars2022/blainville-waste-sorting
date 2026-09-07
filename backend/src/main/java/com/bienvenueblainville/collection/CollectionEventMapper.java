package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface CollectionEventMapper {
    List<CollectionEvent> findUpcoming(
            @Param("sector") Sector sector,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<CollectionEvent> findAll();

    Optional<CollectionEvent> findById(@Param("id") Long id);

    void insert(Map<String, Object> params);

    void update(
            @Param("id") Long id,
            @Param("collectionDate") LocalDate collectionDate,
            @Param("sector") Sector sector,
            @Param("collectionType") CollectionType collectionType,
            @Param("binColor") BinColor binColor,
            @Param("noteFr") String noteFr,
            @Param("noteEn") String noteEn,
            @Param("noteZh") String noteZh,
            @Param("sourceUrl") String sourceUrl
    );

    void delete(@Param("id") Long id);
}
