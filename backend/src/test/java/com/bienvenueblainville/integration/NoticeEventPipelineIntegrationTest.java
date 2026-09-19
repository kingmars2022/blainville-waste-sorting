package com.bienvenueblainville.integration;

import com.bienvenueblainville.audit.AuditRecord;
import com.bienvenueblainville.audit.AuditStore;
import com.bienvenueblainville.events.NoticeEvent;
import com.bienvenueblainville.events.NotificationOutbox;
import com.bienvenueblainville.events.OutboxMapper;
import com.bienvenueblainville.events.OutboxRelay;
import com.bienvenueblainville.notice.SpecialNotice;
import com.bienvenueblainville.notice.SpecialNoticeService;
import com.bienvenueblainville.notice.dto.NoticeRequest;
import com.bienvenueblainville.support.InMemoryMongo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The whole event path, with every piece real: MySQL for the outbox, an
 * embedded Kafka broker, two consumer groups, and a MongoDB wire-protocol
 * server for the audit trail.
 *
 * <p>What this is really testing is the <b>transactional outbox</b>. The naive
 * alternative — publishing to the broker from inside the service method — is a
 * dual write: the database commit and the broker publish can fail
 * independently, so residents get told about a notice that was rolled back, or
 * never told about one that exists. Only a test that can observe the database
 * row and the broker separately can show the difference, which is why this one
 * drains the relay by hand instead of waiting for the scheduler.
 */
@Tag("integration")
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = NoticeEvent.TOPIC)
class NoticeEventPipelineIntegrationTest {
    private static final String MARKER = "notice-event-pipeline-test";

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.events.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        // The document store, so the audit assertions below exercise the
        // MongoDB implementation rather than the MySQL default.
        registry.add("app.audit.store", () -> "mongodb");
        registry.add("spring.data.mongodb.uri", InMemoryMongo::connectionString);
    }

    @Autowired
    private SpecialNoticeService notices;

    @Autowired
    private OutboxMapper outbox;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private AuditStore auditStore;

    @Autowired
    private NotificationOutbox notifications;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from outbox_event where aggregate_id in "
                + "(select id from special_notice where source_url = ?)", MARKER);
        jdbc.update("delete from special_notice where source_url = ?", MARKER);
    }

    @Test
    void theEventIsCommittedWithTheNoticeAndOnlyThenReachesTheBroker() {
        long before = outbox.countUnpublished();

        SpecialNotice created = notices.create(request("Collecte reportee"));

        // Committed with the notice, in the same transaction - and not yet sent.
        List<com.bienvenueblainville.events.OutboxEntry> pending = outbox.findUnpublished(50);
        assertThat(outbox.countUnpublished()).isEqualTo(before + 1);
        assertThat(pending).anySatisfy(entry -> {
            assertThat(entry.aggregateId()).isEqualTo(created.id());
            assertThat(entry.eventType()).isEqualTo("notice.created");
            assertThat(entry.publishedAt()).isNull();
        });

        assertThat(relay.publishPending()).isPositive();
        assertThat(outbox.countUnpublished()).isEqualTo(before);
    }

    @Test
    void bothConsumerGroupsReceiveTheSameEvent() {
        // Two independent groups on one topic is the only thing here a direct
        // method call could not do: each tracks its own offset, so a slow or
        // broken consumer cannot stall the other.
        SpecialNotice created = notices.create(request("Avis de tempete"));
        relay.publishPending();

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditRecord> trail = auditStore.findByEntity("notice", created.id());
            assertThat(trail).hasSize(1);
            assertThat(trail.get(0).action()).isEqualTo("created");
            assertThat(trail.get(0).after()).isNotNull();
        });

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auditStore.findByEntity("notice", created.id()))
                        .allSatisfy(record -> assertThat(notifications.hasHandled(record.eventId())).isTrue()));
    }

    @Test
    void theAuditTrailRecordsBothSidesOfAnUpdate() {
        SpecialNotice created = notices.create(request("Titre initial"));
        notices.update(created.id(), request("Titre corrige"));
        relay.publishPending();

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<AuditRecord> trail = auditStore.findByEntity("notice", created.id());
            assertThat(trail).hasSize(2);

            AuditRecord update = trail.get(1);
            assertThat(update.action()).isEqualTo("updated");
            // A trail that records only the new value cannot answer "what did
            // it say before?", which is the question an audit trail exists for.
            assertThat(update.before().toString()).contains("Titre initial");
            assertThat(update.after().toString()).contains("Titre corrige");
        });
    }

    @Test
    void replayingAnEventDoesNotDoubleCountIt() {
        // Delivery is at-least-once by design, so the consumer has to make the
        // effect exactly-once. Asserting that directly is more useful than
        // trusting the broker not to redeliver.
        SpecialNotice created = notices.create(request("Avis unique"));
        relay.publishPending();

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(auditStore.findByEntity("notice", created.id())).hasSize(1));

        AuditRecord recorded = auditStore.findByEntity("notice", created.id()).get(0);
        auditStore.record(recorded);
        auditStore.record(recorded);

        assertThat(auditStore.findByEntity("notice", created.id())).hasSize(1);
    }

    @Test
    void theAuditTrailIsBackedByTheConfiguredStore() {
        assertThat(auditStore.backendName()).isEqualTo("mongodb");
    }

    private NoticeRequest request(String titleFr) {
        return new NoticeRequest(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(3),
                titleFr, "Storm notice", "风暴通知",
                "Corps FR", "Body EN", "正文", MARKER, true);
    }
}
