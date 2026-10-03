package com.bienvenueblainville.collection;

import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CollectionHolidayMapper {
    /**
     * Holidays on or after {@code from}. The top-up only ever generates
     * forward, so loading the whole table would be loading the past.
     */
    List<CollectionHoliday> findActiveFrom(LocalDate from);
}
