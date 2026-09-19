package com.bienvenueblainville.events;

import java.time.Instant;

public record OutboxEntry(
        Long id,
        String eventId,
        String aggregateType,
        Long aggregateId,
        String eventType,
        String payload,
        Instant occurredAt,
        Instant publishedAt
) {
}
