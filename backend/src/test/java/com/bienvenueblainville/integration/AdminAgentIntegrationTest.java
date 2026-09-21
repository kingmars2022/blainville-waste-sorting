package com.bienvenueblainville.integration;

import com.bienvenueblainville.agent.AgentPlanStore;
import com.bienvenueblainville.agent.AgentToolCatalog;
import com.bienvenueblainville.agent.AgentToolName;
import com.bienvenueblainville.agent.PlannedStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin agent's write path, against real MySQL and real Redis.
 *
 * <p>No model is involved and none is needed. Approval and execution are
 * ordinary requests carrying a plan id, so everything that happens <em>after</em>
 * the model — validation, ownership, single-use plans, partial-failure
 * reporting, and the writes themselves — is testable without spending a token.
 * What the model does with an instruction is covered separately in
 * {@code AdminAgentServiceTest} with canned responses.
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AdminAgentIntegrationTest {
    private static final String MARKER = "admin-agent-integration-test";

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentPlanStore planStore;

    @Autowired
    private AgentToolCatalog catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from collection_event where source_url = ?", MARKER);
        jdbc.update("delete from special_notice where source_url = ?", MARKER);
    }

    @Test
    void planningIsUnavailableWithoutAnApiKeyAndSaysWhy() throws Exception {
        // The suite runs without ANTHROPIC_API_KEY, which is the point: an
        // administrator who has not configured one must get an explanation,
        // not a stack trace, and must be told the forms still work.
        mockMvc.perform(post("/api/admin/agent/plan")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instruction", "Move Thursday's organics to Friday"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("admin forms")));
    }

    @Test
    void aResidentCannotReachTheAgentAtAll() throws Exception {
        mockMvc.perform(post("/api/admin/agent/plan")
                        .header("Authorization", "Bearer " + residentToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("instruction", "delete everything"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void executingAnApprovedPlanWritesToTheDatabase() throws Exception {
        String planId = storePlan(1L, new PlannedStep(AgentToolName.create_collection, Map.of(
                "collectionDate", "2026-11-04",
                "sector", "north",
                "collectionType", "bulky",
                "binColor", "none",
                "sourceUrl", MARKER), "Add a bulky collection"));

        assertThat(countCollections()).isZero();

        catalog.executeWrite(planStore.consume(planId, 1L).steps().get(0));

        assertThat(countCollections()).isOne();
    }

    @Test
    void aPlanIsSingleUse() throws Exception {
        String planId = storePlan(1L, new PlannedStep(AgentToolName.delete_collection,
                Map.of("id", 999999), "Delete collection event 999999"));

        planStore.consume(planId, 1L);

        // Consumed on first use, so a replay cannot re-run stale steps against
        // state the first run already changed.
        assertThatThrownBy(() -> planStore.consume(planId, 1L))
                .hasMessageContaining("expired or was already run");
    }

    @Test
    void twoApprovalsArrivingAtOnceRunThePlanOnce() throws Exception {
        // The bug this guards: consume() used to GET then DELETE. Two approvals
        // landing together both read the plan before either delete happened,
        // and every write in it ran twice. A comment said "single-use"; nothing
        // enforced it. Repeated, because a race that only sometimes loses is
        // still a race.
        for (int attempt = 0; attempt < 25; attempt++) {
            String planId = storePlan(1L, new PlannedStep(AgentToolName.delete_collection,
                    Map.of("id", 999999), "Delete collection event 999999"));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch bothReady = new CountDownLatch(2);
            AtomicInteger succeeded = new AtomicInteger();

            try {
                List<Future<?>> races = List.of(
                        pool.submit(() -> race(bothReady, planId, succeeded)),
                        pool.submit(() -> race(bothReady, planId, succeeded)));
                for (Future<?> race : races) {
                    race.get(10, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(succeeded.get())
                    .as("exactly one of two concurrent approvals may be handed the plan (attempt %d)", attempt)
                    .isEqualTo(1);
        }
    }

    private void race(CountDownLatch bothReady, String planId, AtomicInteger succeeded) {
        try {
            // Both threads sit here until the other arrives, so they reach
            // Redis as close together as two threads can.
            bothReady.countDown();
            bothReady.await(5, TimeUnit.SECONDS);
            planStore.consume(planId, 1L);
            succeeded.incrementAndGet();
        } catch (ResponseStatusException expectedForTheLoser) {
            // One of the two must lose; that is the assertion above.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void oneAdministratorCannotExecuteAnotherOnesPlan() throws Exception {
        String planId = storePlan(1L, new PlannedStep(AgentToolName.delete_notice,
                Map.of("id", 1), "Delete notice 1 permanently"));

        // Approval means "I read this and accept it", which nobody can do on
        // someone else's behalf. Reported as not-found so plan ids are not
        // probeable.
        assertThatThrownBy(() -> planStore.consume(planId, 2L))
                .hasMessageContaining("expired or was already run");
    }

    @Test
    void invalidArgumentsAreRejectedBeforeTheyReachTheDatabase() {
        // The @NotNull annotations on the request records are applied by Spring
        // at the controller boundary, and the agent does not cross it. Without
        // an explicit validate() call this would reach MyBatis instead.
        PlannedStep missingDate = new PlannedStep(AgentToolName.create_collection, Map.of(
                "sector", "north", "collectionType", "bulky", "binColor", "none"), "Incomplete");

        assertThatThrownBy(() -> catalog.executeWrite(missingDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collectionDate");

        assertThat(countCollections()).isZero();
    }

    @Test
    void readToolsReturnTheRealSchedule() throws Exception {
        // What the model plans against. If this ever returned something other
        // than the live rows, every plan built on it would be wrong.
        JsonNode rows = objectMapper.readTree(catalog.executeRead(AgentToolName.list_collections));

        assertThat(rows.isArray()).isTrue();
        assertThat(rows.size()).isEqualTo(jdbc.queryForObject(
                "select count(*) from collection_event where collection_date >= current_date()",
                Integer.class));
    }

    @Test
    void theScheduleToolShowsTheFutureRatherThanEverythingThatEverHappened() throws Exception {
        // This used to be collections.all(). Every collection there had ever
        // been went into a model prompt, was paid for on every planning call,
        // and grew for ever - the calendar extends itself and nothing prunes
        // what is behind. Bounding it forward costs nothing the model needed:
        // a collection that already happened cannot usefully be moved.
        int past = jdbc.queryForObject(
                "select count(*) from collection_event where collection_date < current_date()",
                Integer.class);
        assertThat(past)
                .as("the seeded calendar has to contain past rows or this proves nothing")
                .isPositive();

        JsonNode rows = objectMapper.readTree(catalog.executeRead(AgentToolName.list_collections));

        assertThat(rows).isNotEmpty();
        LocalDate today = LocalDate.now();
        for (JsonNode row : rows) {
            assertThat(LocalDate.parse(row.get("collectionDate").asText()))
                    .isAfterOrEqualTo(today);
        }
        // Nearest first: a limit has to cut the far end off, not the near one.
        assertThat(LocalDate.parse(rows.get(0).get("collectionDate").asText()))
                .isBeforeOrEqualTo(LocalDate.parse(
                        rows.get(rows.size() - 1).get("collectionDate").asText()));
    }

    @Test
    void bothReadToolsAreBounded() throws Exception {
        // The cap is a backstop for a bulk import, so it cannot be asserted
        // against the seeded data directly - what can be asserted is that
        // neither listing is the unbounded one any more.
        JsonNode collections = objectMapper.readTree(catalog.executeRead(AgentToolName.list_collections));
        JsonNode notices = objectMapper.readTree(catalog.executeRead(AgentToolName.list_notices));

        assertThat(collections.size()).isLessThanOrEqualTo(200);
        assertThat(notices.size()).isLessThanOrEqualTo(200);
        assertThat(notices.size()).isLessThanOrEqualTo(
                jdbc.queryForObject("select count(*) from special_notice", Integer.class));
    }

    private String storePlan(Long adminUserId, PlannedStep... steps) {
        String planId = AgentPlanStore.newPlanId();
        planStore.save(new com.bienvenueblainville.agent.AgentPlan(
                planId, adminUserId, "test instruction", "test narrative",
                java.util.List.of(steps), java.time.Instant.now()));
        return planId;
    }

    private int countCollections() {
        return jdbc.queryForObject(
                "select count(*) from collection_event where source_url = ?", Integer.class, MARKER);
    }

    private String adminToken() throws Exception {
        return tokenFrom(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", IntegrationEnvironment.ADMIN_EMAIL,
                        "password", IntegrationEnvironment.ADMIN_PASSWORD))));
    }

    private String residentToken() throws Exception {
        return tokenFrom(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", "agent-probe-" + UUID.randomUUID() + "@example.com",
                        "password", "ResidentPass123"))));
    }

    private String tokenFrom(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
}
