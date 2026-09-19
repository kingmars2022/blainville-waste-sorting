package com.bienvenueblainville.collection;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CollectionScheduleRuleMapper {
    List<CollectionScheduleRule> findActive();

    void advanceGeneratedThrough(@Param("id") Long id, @Param("generatedThrough") LocalDate generatedThrough);
}
