package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CollectionHolidayMapper {
    /**
     * Holidays on or after {@code from}. The top-up only ever generates
     * forward, so loading the whole table would be loading the past.
     */
    List<CollectionHoliday> findActiveFrom(LocalDate from);

    List<CollectionHoliday> findAll();

    CollectionHoliday findById(Long id);

    CollectionHoliday findByDateAndSector(
            @Param("holidayDate") LocalDate holidayDate, @Param("sector") Sector sector);

    void insert(CollectionHoliday holiday);

    int update(CollectionHoliday holiday);

    int delete(Long id);
}
