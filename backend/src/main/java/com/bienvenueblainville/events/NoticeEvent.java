package com.bienvenueblainville.events;

import java.time.Instant;

/**
 * What consumers receive. Deliberately a snapshot rather than a pointer:
 * an event that says only "notice 7 changed" forces every consumer back to the
 * database, and by the time a slow consumer gets there the row may have
 * changed again — so the audit trail would record the wrong state. Carrying
 * the values makes the event true about a moment in time.
 *
 * @param eventId    stable across redeliveries; consumers dedupe on it
 * @param action     created / updated / deleted
 * @param before     null for a creation
 * @param after      null for a deletion
 */
public record NoticeEvent(
        String eventId,
        String action,
        Long noticeId,
        String actor,
        Instant occurredAt,
        Object before,
        Object after
) {
    public static final String TOPIC = "blainville.notice.events";
}
