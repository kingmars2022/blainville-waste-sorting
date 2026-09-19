package com.bienvenueblainville.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * The second consumer: works out who would be told about a new notice.
 *
 * <p>It stops at counting rather than sending, because this project has no
 * push channel and inventing one to justify the consumer would be exactly the
 * kind of thing the rest of this codebase argues against. What it does
 * demonstrate is the property that matters: two independent consumer groups
 * reading the same topic, each with its own offset, so neither can stall the
 * other.
 */
@Component
public class NotificationOutbox {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutbox.class);

    private final JdbcTemplate jdbc;

    /**
     * In-memory deduplication, because this consumer has no table of its own
     * to hold a unique key. It resets on restart, which for a counter that
     * nobody bills against is an acceptable trade — a real notification sender
     * would need the same durable dedupe the audit store has.
     */
    private final Set<String> handled = ConcurrentHashMap.newKeySet();

    public NotificationOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void queueForActiveNotice(String eventId, Long noticeId) {
        if (!handled.add(eventId)) {
            return;
        }

        Long recipients = jdbc.queryForObject(
                "select count(*) from user_preference where reminder_enabled = true", Long.class);

        log.info("Notice {} would notify {} resident(s) with reminders enabled", noticeId, recipients);
    }

    /** Exposed for the integration test, which asserts both consumers ran. */
    public boolean hasHandled(String eventId) {
        return handled.contains(eventId);
    }
}
