package com.bienvenueblainville.audit;

import java.time.Instant;

/**
 * One change, with the state on either side of it.
 *
 * @param eventId  the deduplication key; the same change replayed must not
 *                 produce a second record
 * @param before   null for a creation
 * @param after    null for a deletion
 */
public record AuditRecord(
        String eventId,
        String entityType,
        Long entityId,
        String action,
        String actor,
        Instant occurredAt,
        Object before,
        Object after
) {
}
