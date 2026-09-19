package com.bienvenueblainville.events;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboxMapper {
    /** Inserted inside the caller's transaction - that is the entire point. */
    void insert(
            @Param("eventId") String eventId,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") Long aggregateId,
            @Param("eventType") String eventType,
            @Param("payload") String payload
    );

    List<OutboxEntry> findUnpublished(@Param("limit") int limit);

    void markPublished(@Param("id") Long id);

    long countUnpublished();
}
