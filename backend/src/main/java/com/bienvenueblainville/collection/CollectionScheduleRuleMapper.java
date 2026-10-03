package com.bienvenueblainville.collection;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface CollectionScheduleRuleMapper {
    List<CollectionScheduleRule> findActive();

    void advanceGeneratedThrough(@Param("id") Long id, @Param("generatedThrough") LocalDate generatedThrough);

    /**
     * Pulls every rule's high-water mark back to {@code generatedThrough} when
     * it has gone past it, so the next top-up regenerates the window a holiday
     * change has invalidated.
     */
    void rewindGeneratedThrough(@Param("generatedThrough") LocalDate generatedThrough);
}
