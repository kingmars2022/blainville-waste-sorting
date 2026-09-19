package com.bienvenueblainville.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import com.bienvenueblainville.agent.dto.AgentPlanView;
import com.bienvenueblainville.assistant.AnthropicClientProvider;
import com.bienvenueblainville.assistant.AssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The agent's tool loop, with Claude mocked.
 *
 * <p>Mocking the model is right here rather than a compromise: what is being
 * tested is what <em>this</em> code does with a model's output, and the
 * dangerous cases — a model that calls a write tool, a model that loops
 * forever — are ones a real API would only produce by luck. A canned response
 * makes them reproducible.
 */
class AdminAgentServiceTest {
    private static final Long ADMIN_ID = 42L;

    @Mock
    private AnthropicClient client;
    @Mock
    private MessageService messages;
    @Mock
    private AnthropicClientProvider clientProvider;
    @Mock
    private AgentToolCatalog catalog;
    @Mock
    private AgentPlanStore planStore;

    private AdminAgentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(clientProvider.client()).thenReturn(Optional.of(client));
        when(client.messages()).thenReturn(messages);
        when(catalog.tools()).thenReturn(List.of());
        when(planStore.save(any())).thenAnswer(call -> ((AgentPlan) call.getArgument(0)).id());

        service = new AdminAgentService(
                clientProvider, catalog, planStore,
                new AssistantProperties("template", "claude-opus-5", "key",
                        new AssistantProperties.RateLimit(30)));
    }

    @Test
    void recordsWriteToolsInsteadOfRunningThem() throws Exception {
        // The safety property of the entire feature. A tool runner would have
        // executed this call; the hand-written loop must not.
        when(messages.create(any(MessageCreateParams.class)))
                .thenReturn(toolUseMessage("t1", "create_collection", """
                        {"collectionDate": "2026-10-02", "sector": "north",
                         "collectionType": "bulky", "binColor": "none"}
                        """))
                .thenReturn(textMessage("Proposing one extra bulky collection."));

        AgentPlanView plan = service.plan("Add a bulky pickup on October 2 for the north", ADMIN_ID);

        verify(catalog, never()).executeWrite(any());
        assertThat(plan.steps()).singleElement().satisfies(step -> {
            assertThat(step.tool()).isEqualTo("create_collection");
            // Summarised from the arguments that would actually run, not from
            // anything the model wrote about itself.
            assertThat(step.summary())
                    .isEqualTo("Add a bulky collection for sector north on 2026-10-02 (none bin)");
        });
        assertThat(plan.narrative()).isEqualTo("Proposing one extra bulky collection.");
        assertThat(plan.planId()).isNotNull();
    }

    @Test
    void runsReadToolsImmediatelySoThePlanIsBuiltOnRealData() throws Exception {
        when(catalog.executeRead(AgentToolName.list_collections)).thenReturn("[{\"id\":7}]");
        when(messages.create(any(MessageCreateParams.class)))
                .thenReturn(toolUseMessage("t1", "list_collections", "{}"))
                .thenReturn(textMessage("There are no Thursday organics to move."));

        service.plan("Move Thursday organics to Friday", ADMIN_ID);

        verify(catalog).executeRead(AgentToolName.list_collections);
    }

    @Test
    void proposingNothingProducesNoPlanToApprove() throws Exception {
        // An empty plan is a legitimate answer to an unclear instruction, and
        // the UI keys off a null id to decide whether to show a confirm button.
        when(messages.create(any(MessageCreateParams.class)))
                .thenReturn(textMessage("Which Thursday did you mean? There are four this month."));

        AgentPlanView plan = service.plan("move it", ADMIN_ID);

        assertThat(plan.steps()).isEmpty();
        assertThat(plan.planId()).isNull();
        verify(planStore, never()).save(any());
    }

    @Test
    void stopsCallingTheModelAtTheTurnCeiling() throws Exception {
        // A model that keeps calling tools must cost a bounded number of
        // requests, not an afternoon's budget.
        when(catalog.executeRead(any())).thenReturn("[]");
        when(messages.create(any(MessageCreateParams.class)))
                .thenReturn(toolUseMessage("t1", "list_collections", "{}"));

        service.plan("keep going", ADMIN_ID);

        verify(messages, times(8)).create(any(MessageCreateParams.class));
    }

    // ------------------------------------------------------------- fixtures

    /**
     * Fixtures are built by deserializing real API-shaped JSON rather than
     * through the SDK's builders.
     *
     * <p>Two reasons, and the second is the important one. The builders demand
     * every optional field be set explicitly, so a hand-built fixture is mostly
     * noise. More usefully, JSON is what the API actually sends — a fixture in
     * that shape stays honest about the payload this code has to cope with,
     * and would notice if a future SDK version changed how it reads one.
     */
    private static Message message(String contentJson, String stopReason) {
        String json = """
                {
                  "id": "msg_test",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-opus-5",
                  "content": %s,
                  "stop_reason": "%s",
                  "stop_sequence": null,
                  "usage": {"input_tokens": 10, "output_tokens": 20}
                }
                """.formatted(contentJson, stopReason);
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (Exception e) {
            throw new IllegalStateException("Bad test fixture", e);
        }
    }

    private static Message textMessage(String text) {
        return message("""
                [{"type": "text", "text": %s}]
                """.formatted(quote(text)), "end_turn");
    }

    private static Message toolUseMessage(String id, String name, String inputJson) {
        return message("""
                [{"type": "tool_use", "id": "%s", "name": "%s", "input": %s}]
                """.formatted(id, name, inputJson), "tool_use");
    }

    private static String quote(String text) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
