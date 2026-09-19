package com.bienvenueblainville.events;

import com.bienvenueblainville.audit.AuditRecord;
import com.bienvenueblainville.audit.AuditStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Two consumers on one topic, in separate groups — which is the only reason a
 * broker is doing any work here that a direct method call could not.
 *
 * <p>Both listeners honour {@code app.events.enabled}. Kafka is optional
 * infrastructure here in the same way MongoDB is: the outbox still records
 * every change into MySQL whether or not a broker exists, so turning the
 * consumers off loses the fan-out, not the events - they stay on the table,
 * waiting.
 *
 * <p>Being honest about that: with exactly these two consumers, a MySQL outbox
 * and a worker thread would do the same job with one fewer system to operate,
 * and the reviewer of this project said so. What the broker buys is that a
 * third consumer — a push-notification service, an open-data export, a
 * different team's dashboard — subscribes without anyone editing this file,
 * and that a slow consumer cannot slow down a fast one because each group
 * tracks its own offset. Whether that is worth a broker depends on whether
 * those consumers ever arrive.
 */
@Component
public class NoticeEventConsumers {
    private static final Logger log = LoggerFactory.getLogger(NoticeEventConsumers.class);

    private final AuditStore auditStore;
    private final NotificationOutbox notifications;
    private final ObjectMapper objectMapper;

    public NoticeEventConsumers(AuditStore auditStore, NotificationOutbox notifications, ObjectMapper objectMapper) {
        this.auditStore = auditStore;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the permanent record of who changed what.
     *
     * <p>Idempotent by event id rather than by retry count: the relay is
     * at-least-once, so this method must be safe to run twice on the same
     * event. An audit trail that double-counts a change is not an audit trail.
     */
    @KafkaListener(topics = NoticeEvent.TOPIC, groupId = "audit-writer",
            autoStartup = "${app.events.enabled:true}")
    public void writeAudit(String payload) {
        NoticeEvent event = read(payload);
        if (event == null) {
            return;
        }

        auditStore.record(new AuditRecord(
                event.eventId(),
                "notice",
                event.noticeId(),
                event.action(),
                event.actor(),
                event.occurredAt() == null ? Instant.now() : event.occurredAt(),
                event.before(),
                event.after()));
    }

    /**
     * Records who should be told. Separate group, so a slow or broken
     * notification path cannot stall the audit trail, and vice versa — the
     * property that a direct method call cannot give you.
     */
    @KafkaListener(topics = NoticeEvent.TOPIC, groupId = "notification-outbox",
            autoStartup = "${app.events.enabled:true}")
    public void queueNotifications(String payload) {
        NoticeEvent event = read(payload);
        if (event == null || !"created".equals(event.action())) {
            // Only a new notice is worth interrupting a resident for. Edits and
            // deletions are audit material, not notifications.
            return;
        }

        notifications.queueForActiveNotice(event.eventId(), event.noticeId());
    }

    private NoticeEvent read(String payload) {
        try {
            return objectMapper.readValue(payload, NoticeEvent.class);
        } catch (Exception e) {
            // A payload this consumer cannot parse will never become parseable,
            // so failing forever would block the partition behind it. Logged
            // and skipped; in production this is where a dead-letter topic goes.
            log.error("Unreadable notice event, skipping: {}", payload, e);
            return null;
        }
    }
}
