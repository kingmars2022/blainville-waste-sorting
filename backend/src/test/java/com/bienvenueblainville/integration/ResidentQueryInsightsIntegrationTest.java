package com.bienvenueblainville.integration;

import com.bienvenueblainville.insights.GuideGap;
import com.bienvenueblainville.insights.QueryLogStore;
import com.bienvenueblainville.support.InMemoryMongo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Residents ask, the city finds out what it has not written down.
 *
 * <p>Runs the whole path with nothing stubbed: a real question through the real
 * endpoint, a real Kafka broker carrying it off the request path, a real
 * MongoDB aggregation turning a pile of questions into a work list.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "blainville.resident.queries")
class ResidentQueryInsightsIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.events.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", () -> System.getProperty("spring.embedded.kafka.brokers"));
        registry.add("spring.data.mongodb.uri", InMemoryMongo::connectionString);
        // Plenty of headroom: this test asks a lot of questions on purpose.
        registry.add("app.assistant.rate-limit.requests-per-hour", () -> 500);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QueryLogStore store;

    @Autowired
    private MongoTemplate mongo;

    @AfterEach
    void cleanUp() {
        mongo.getCollection("resident_queries").drop();
    }

    @Test
    void aQuestionTheGuideCannotAnswerBecomesAWorkListEntry() throws Exception {
        // Three residents ask about the same thing, in two languages. Before
        // this stream existed, all three got a polite "check blainville.ca" and
        // the city learned nothing.
        ask("aquarium", "en");
        ask("aquarium", "en");
        ask("aquarium", "fr");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            List<GuideGap> gaps = store.unansweredTerms(30, 10);

            assertThat(gaps).anySatisfy(gap -> {
                assertThat(gap.term()).isEqualTo("aquarium");
                assertThat(gap.asks()).isEqualTo(3);
                // Asked in more than one language is a bigger gap than one.
                assertThat(gap.languages()).containsExactlyInAnyOrder("en", "fr");
                // The originals, so whoever writes the entry can see the words
                // residents actually use.
                assertThat(gap.examples()).isNotEmpty();
            });
        });
    }

    @Test
    void answeredQuestionsDoNotAppearInTheGapReport() throws Exception {
        // The report is a work list, not a traffic log. A question the guide
        // answered is not an entry anybody needs to write.
        ask("Where does soiled cardboard go?", "en");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(store.count()).isPositive());

        assertThat(store.unansweredTerms(30, 10))
                .noneSatisfy(gap -> assertThat(gap.term()).contains("cardboard"));
    }

    @Test
    void theSummaryReportsHowMuchOfTheTrafficTheGuideCouldNotAnswer() throws Exception {
        ask("Where does soiled cardboard go?", "en");
        ask("aquarium", "en");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Map<String, Object> summary = store.summary(30);

            assertThat(((Number) summary.get("questions")).longValue()).isEqualTo(2);
            assertThat(((Number) summary.get("answered")).longValue()).isEqualTo(1);
            assertThat(((Number) summary.get("unanswered")).longValue()).isEqualTo(1);
            // The number an administrator watches: a rising figure means the
            // guide is drifting behind what residents actually own.
            assertThat(((Number) summary.get("unansweredRate")).doubleValue()).isEqualTo(50.0);
        });
    }

    @Test
    void recordingTheSameQuestionTwiceCountsItOnce() {
        // The consumer is at-least-once like every other one here, so a
        // redelivered question must not inflate the report it feeds.
        var event = new com.bienvenueblainville.insights.QueryEvent(
                "fixed-id", "text", com.bienvenueblainville.common.LanguageCode.en,
                "aquarium", "aquarium", false, 0d, List.of(), java.time.Instant.now());

        store.record(event);
        store.record(event);
        store.record(event);

        assertThat(store.count()).isEqualTo(1);
        assertThat(store.unansweredTerms(30, 10))
                .singleElement()
                .satisfies(gap -> assertThat(gap.asks()).isEqualTo(1));
    }

    @Test
    void theStreamNeverCarriesAnythingThatIdentifiesAResident() throws Exception {
        ask("aquarium", "en");

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(store.count()).isPositive());

        // The only use for this data is deciding which entries to write next.
        // Anything that could identify who asked would be collection for its
        // own sake, so the document is asserted to have no room for it.
        org.bson.Document stored = mongo.getCollection("resident_queries").find().first();
        assertThat(stored).isNotNull();
        assertThat(stored.keySet()).doesNotContain("ip", "remoteAddr", "userId", "email", "photoId");
    }

    private void ask(String question, String language) throws Exception {
        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", question, "language", language))))
                .andExpect(status().isOk());
    }
}
