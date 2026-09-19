package com.bienvenueblainville.events;

import com.bienvenueblainville.notification.ResidentNotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The second consumer: puts a published notice into the inbox of every
 * resident who asked to be told.
 *
 * <p>It used to count recipients and log the number, because this application
 * had no push channel and inventing one to justify the consumer would have
 * been backwards. An in-app inbox needs no external service, so the consumer
 * now does the real thing.
 *
 * <p>Deduplication moved with it, from an in-memory set to a unique key on
 * {@code (user_id, event_id)}. The in-memory version reset on restart, which
 * is precisely when a consumer is most likely to replay from its last
 * committed offset - so the old guard was weakest exactly when it was needed.
 */
@Component
public class NotificationOutbox {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutbox.class);

    private final ResidentNotificationMapper notifications;

    public NotificationOutbox(ResidentNotificationMapper notifications) {
        this.notifications = notifications;
    }

    public void queueForActiveNotice(String eventId, Long noticeId) {
        // One statement for the whole fan-out: the recipient list is a query
        // the database can answer, and reading it into the application only to
        // write it back would turn one round trip into one per resident.
        int delivered = notifications.fanOutToResidentsWithReminders(noticeId, eventId);

        // Zero is the normal result for a redelivered event, not a problem.
        log.info("Notice {} delivered to {} resident inbox(es)", noticeId, delivered);
    }
}
