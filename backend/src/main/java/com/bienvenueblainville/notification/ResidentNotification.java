package com.bienvenueblainville.notification;

import java.time.Instant;

/**
 * One notice, delivered to one resident's inbox.
 *
 * <p>The notice text is joined in at read time rather than copied here: a
 * correction to a notice should reach everyone who has not read it yet, which
 * a snapshot would prevent. That is the opposite choice from the audit trail,
 * where the snapshot <em>is</em> the record — the difference is that one is
 * history and this is a current message.
 */
public record ResidentNotification(
        Long id,
        Long noticeId,
        String titleFr,
        String titleEn,
        String titleZh,
        String bodyFr,
        String bodyEn,
        String bodyZh,
        Instant createdAt,
        Instant readAt
) {
}
