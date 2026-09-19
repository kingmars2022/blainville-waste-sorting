package com.bienvenueblainville.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The grounded sorting assistant, end to end: real MySQL with the seeded
 * guide, the real full-text indexes from V7, real Redis for the rate limit.
 *
 * <p>Retrieval quality cannot be asserted with mocks — the whole feature is
 * the interaction between MySQL's two full-text parsers, the curated keyword
 * table, and the stopword filtering in front of them. A mocked mapper would
 * assert only that the plumbing is connected.
 *
 * <p>The broader quality numbers (precision@1 and refusal accuracy over an 18
 * question set, in all three languages) are measured by
 * {@code docs/verification/assistant_eval.py} against the running application.
 * What is pinned here are the properties that must never regress silently.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AssistantIntegrationTest {
    private static final int RATE_LIMIT = 5;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        registry.add("app.assistant.provider", () -> "template");
        registry.add("app.assistant.rate-limit.requests-per-hour", () -> RATE_LIMIT);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private com.bienvenueblainville.assistant.AnswerCache answerCache;

    @BeforeEach
    void clearRedisState() {
        drop("assistant:ratelimit:*");
        // Answers are cached now, so a previous test's answer would otherwise
        // satisfy the next one without exercising retrieval at all.
        drop(com.bienvenueblainville.assistant.AnswerCache.PREFIX + "*");
    }

    private void drop(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void answersFromTheGuideInFrench() throws Exception {
        JsonNode answer = ask("Où va une boîte à pizza sale ?", "fr");

        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("sources").get(0).get("name").asText())
                .isEqualTo("Papiers et cartons souilles d aliments");
        assertThat(answer.get("answer").asText()).contains("bac brun");
    }

    @Test
    void answersFromTheGuideInEnglish() throws Exception {
        JsonNode answer = ask("Where does soiled cardboard go?", "en");

        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("sources").get(0).get("name").asText())
                .isEqualTo("Food-soiled paper and cardboard");
    }

    @Test
    void answersFromTheGuideInChinese() throws Exception {
        // Chinese is the reason V7 carries a second full-text parser at all:
        // 废电池怎么处理 has no whitespace for the default parser to split on,
        // so without the ngram index this returns nothing.
        JsonNode answer = ask("废电池怎么处理？", "zh");

        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.get("sources").get(0).get("name").asText()).contains("ecocentre");
    }

    @Test
    void refusesWhenTheGuideDoesNotCoverTheQuestion() throws Exception {
        JsonNode answer = ask("what is the capital of Mongolia", "en");

        assertThat(answer.get("grounded").asBoolean()).isFalse();
        assertThat(answer.get("sources")).isEmpty();
        assertThat(answer.get("answer").asText()).contains("blainville.ca");
    }

    @Test
    void refusesAFrenchQuestionThatOnlyMatchesOnAFunctionWord() throws Exception {
        // The regression that QueryNormalizer exists for. MySQL's stopword list
        // is English-only, so "est" was scored as content and this question
        // came back answered, with household-waste advice.
        JsonNode answer = ask("Quel est le numéro de téléphone du maire ?", "fr");

        assertThat(answer.get("grounded").asBoolean()).isFalse();
    }

    @Test
    void rejectsAQuestionTooLongToBeOne() throws Exception {
        // An unbounded question is an unbounded prompt, and the prompt is the
        // part that costs money.
        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "x".repeat(301),
                                "language", "en"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theQuotaCounterIsInstalledWithAnExpiryByRealRedis() throws Exception {
        // The quota is counted by a Lua script, so that it increments and
        // installs the expiry atomically - a separate INCR and EXPIRE can be
        // interrupted between the two and leave a key that never expires,
        // permanently locking out an address. Unit tests mock the Redis
        // template and cannot show the script runs at all, let alone that the
        // TTL lands.
        ask("Where does soiled cardboard go?", "en");

        Set<String> keys = redis.keys("assistant:ratelimit:*");
        assertThat(keys).hasSize(1);

        String key = keys.iterator().next();
        assertThat(redis.opsForValue().get(key)).isEqualTo("1");
        assertThat(redis.getExpire(key)).isGreaterThan(0L);
    }

    @Test
    void askingTheSameThingTwiceIsAnsweredFromCacheWithoutRepeatingTheWork() throws Exception {
        // The same twenty materials are what a municipal guide gets asked
        // about, so a repeat is the normal case rather than an edge one.
        JsonNode first = ask("Where does soiled cardboard go?", "en");

        assertThat(redis.keys(com.bienvenueblainville.assistant.AnswerCache.PREFIX + "*"))
                .hasSize(1);

        JsonNode second = ask("Where does soiled cardboard go?", "en");
        assertThat(second.get("answer").asText()).isEqualTo(first.get("answer").asText());
    }

    @Test
    void punctuationAndCaseDoNotProduceASecondCacheEntry() throws Exception {
        // Normalized before hashing, or the cache would miss on exactly the
        // variations residents actually type.
        ask("Where does soiled cardboard go?", "en");
        ask("where does SOILED cardboard go", "en");

        assertThat(redis.keys(com.bienvenueblainville.assistant.AnswerCache.PREFIX + "*"))
                .hasSize(1);
    }

    @Test
    void editingTheSortingGuideDropsCachedAnswers() throws Exception {
        // The failure this prevents: a cached refusal for a material that an
        // administrator has just added. The guide would be right and the
        // assistant would still be saying it does not know.
        ask("Where does soiled cardboard go?", "en");
        assertThat(redis.keys(com.bienvenueblainville.assistant.AnswerCache.PREFIX + "*")).isNotEmpty();

        answerCache.invalidateAll();

        assertThat(redis.keys(com.bienvenueblainville.assistant.AnswerCache.PREFIX + "*")).isEmpty();
    }

    @Test
    void capsHowManyQuestionsOneAddressCanAsk() throws Exception {
        for (int i = 0; i < RATE_LIMIT; i++) {
            ask("Where does soiled cardboard go?", "en");
        }

        mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "Where does soiled cardboard go?",
                                "language", "en"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Too many assistant questions")));
    }

    private JsonNode ask(String question, String language) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/assistant/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", question,
                                "language", language))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
