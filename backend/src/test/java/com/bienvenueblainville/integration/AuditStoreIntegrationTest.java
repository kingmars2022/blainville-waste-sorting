package com.bienvenueblainville.integration;

import com.bienvenueblainville.audit.AuditRecord;
import com.bienvenueblainville.audit.AuditStore;
import com.bienvenueblainville.audit.MongoAuditStore;
import com.bienvenueblainville.audit.MySqlAuditStore;
import com.bienvenueblainville.support.InMemoryMongo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same contract, run against both audit backends.
 *
 * <p>This is the test that makes "why MongoDB?" answerable by comparison
 * rather than by assertion. If the document store were carrying its weight at
 * this scale, one of these two implementations would struggle to satisfy a
 * test the other passes. Neither does — which is the finding, and it is why
 * MySQL is the default.
 */
@Tag("integration")
@SpringBootTest
class AuditStoreIntegrationTest {
    private static final String ENTITY = "audit-store-contract-test";

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("spring.data.mongodb.uri", InMemoryMongo::connectionString);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private ObjectMapper objectMapper;

    private static Stream<String> backends() {
        return Stream.of("mysql-json", "mongodb");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from audit_record where entity_type = ?", ENTITY);
        mongo.getCollection("audit_records").deleteMany(new org.bson.Document("entityType", ENTITY));
    }

    private AuditStore store(String backend) {
        return "mongodb".equals(backend)
                ? new MongoAuditStore(mongo)
                : new MySqlAuditStore(jdbc, objectMapper);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void keepsBothSidesOfAChangeAndReadsThemBack(String backend) {
        AuditStore store = store(backend);
        Instant when = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        store.record(new AuditRecord(UUID.randomUUID().toString(), ENTITY, 1L, "updated",
                "admin@blainville.local", when,
                Map.of("titleFr", "Avant"), Map.of("titleFr", "Apres")));

        List<AuditRecord> trail = store.findByEntity(ENTITY, 1L);
        assertThat(trail).singleElement().satisfies(record -> {
            assertThat(record.action()).isEqualTo("updated");
            assertThat(record.actor()).isEqualTo("admin@blainville.local");
            assertThat(record.before().toString()).contains("Avant");
            assertThat(record.after().toString()).contains("Apres");
            assertThat(record.occurredAt()).isEqualTo(when);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void isIdempotentOnTheEventId(String backend) {
        // Kafka delivery is at-least-once, so recording the same event twice is
        // expected rather than exceptional. Both stores enforce this with a
        // single write - an upsert and an ON DUPLICATE KEY - so two consumers
        // replaying concurrently cannot both decide the event is new.
        AuditStore store = store(backend);
        AuditRecord entry = new AuditRecord(UUID.randomUUID().toString(), ENTITY, 2L, "created",
                "admin@blainville.local", Instant.now().truncatedTo(ChronoUnit.MILLIS),
                null, Map.of("titleFr", "Nouvel avis"));

        store.record(entry);
        store.record(entry);
        store.record(entry);

        assertThat(store.findByEntity(ENTITY, 2L)).hasSize(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void handlesTheNullSideOfCreationsAndDeletions(String backend) {
        AuditStore store = store(backend);
        Instant when = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        store.record(new AuditRecord(UUID.randomUUID().toString(), ENTITY, 3L, "created",
                "system", when, null, Map.of("titleFr", "Cree")));
        store.record(new AuditRecord(UUID.randomUUID().toString(), ENTITY, 3L, "deleted",
                "system", when.plusMillis(10), Map.of("titleFr", "Cree"), null));

        List<AuditRecord> trail = store.findByEntity(ENTITY, 3L);
        assertThat(trail).hasSize(2);
        assertThat(trail.get(0).before()).isNull();
        assertThat(trail.get(1).after()).isNull();
    }
}
