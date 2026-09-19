package com.bienvenueblainville.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Moves committed outbox rows to Kafka.
 *
 * <p>Runs after the transaction, never inside it, so the only thing it can get
 * wrong is sending an event twice — which is why consumers deduplicate on the
 * event id. That is the deliberate trade: at-least-once delivery plus
 * idempotent consumers, instead of a two-phase commit nobody wants to operate.
 *
 * <p>A row is marked published only once the broker has acknowledged it. A
 * crash between the send and the mark therefore replays the event rather than
 * losing it, which is the safe direction for this failure.
 */
@Component
@ConditionalOnProperty(name = "app.events.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /**
     * Bounded so one poll cannot hold the thread for an unbounded time after a
     * backlog builds up. The next tick picks up the rest.
     */
    private static final int BATCH = 100;

    private final OutboxMapper mapper;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxMapper mapper, KafkaTemplate<String, String> kafka) {
        this.mapper = mapper;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${app.events.relay.interval-ms:1000}")
    public void drain() {
        publishPending();
    }

    /** Exposed so tests can drive a drain deterministically instead of waiting. */
    public int publishPending() {
        var pending = mapper.findUnpublished(BATCH);
        int sent = 0;

        for (OutboxEntry entry : pending) {
            try {
                // Keyed by aggregate id, so every event about one notice lands
                // on the same partition and is consumed in the order it
                // happened. Without this, an update can overtake the creation
                // it depends on.
                kafka.send(NoticeEvent.TOPIC, String.valueOf(entry.aggregateId()), entry.payload())
                        .get();
                mapper.markPublished(entry.id());
                sent++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Outbox relay interrupted at event {}; it stays unpublished", entry.eventId());
                break;
            } catch (Exception e) {
                // Stop at the first failure rather than skipping ahead: the
                // ordering guarantee above only holds if a stuck event blocks
                // the ones behind it. The row stays unpublished, so the next
                // tick retries it.
                log.warn("Outbox relay stopped at event {}; it will be retried", entry.eventId(), e);
                break;
            }
        }

        return sent;
    }
}
