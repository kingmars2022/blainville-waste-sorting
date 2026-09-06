package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CollectionEventMapper {
    List<CollectionEvent> findUpcoming(
            @Param("sector") Sector sector,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}

