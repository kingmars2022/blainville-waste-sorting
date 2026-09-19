package com.bienvenueblainville.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.bienvenueblainville.agent.dto.AgentExecutionView;
import com.bienvenueblainville.agent.dto.AgentPlanView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Natural-language administration of the collection schedule and public
 * notices, with a human approval step in the middle.
 *
 * <p>The loop is written out by hand rather than using the SDK's tool runner,
 * and that is the whole design. A tool runner executes every tool the model
 * calls. Here, <b>reads run and writes do not</b>: read tools execute
 * immediately so the model plans against the real schedule with real ids,
 * while every write is recorded as a proposed step and handed back to a person.
 * Nothing in the database changes until an administrator approves the plan.
 *
 * <p>Why bother, when the admin forms already work: a storm delay means moving
 * several rows and publishing a trilingual notice — six or seven form
 * submissions, each a chance to typo a date. One sentence, one screen to check,
 * one approval. It is a convenience layer over the same REST endpoints, not a
 * replacement for them, and it is deliberately limited to the two resources
 * whose content comes from the administrator's own words.
 */
@Service
public class AdminAgentService {
    private static final Logger log = LoggerFactory.getLogger(AdminAgentService.class);

    /**
     * Enough turns for "look at the schedule, then propose a handful of
     * changes"; low enough that a confused model cannot bill its way through
     * the afternoon.
     */
    private static final int MAX_TURNS = 8;

    private static final String SYSTEM = """
            You are an operations assistant for the waste-collection back office
            of Blainville, Quebec. You help a municipal administrator change the
            collection schedule and publish public notices.

            How this works:
            - Read tools run immediately. Use them. Never guess an id, a date or
              a current value - look it up first.
            - Write tools do NOT run when you call them. They are collected into
              a plan that a human administrator reads and approves. So call them
              deliberately and completely, as if they were final.

            Rules:
            - Propose the smallest set of writes that satisfies the request.
            - Notices need French, English and Chinese. Write all three yourself;
              do not leave a language empty or copy one language into another.
            - update_ tools replace the whole record. Carry over the existing
              values of fields you are not changing.
            - Prefer deactivating a notice (active=false) over deleting it,
              unless deletion was explicitly asked for.
            - If the request is ambiguous, or you cannot find what it refers to,
              propose nothing and say what you would need to know. An empty plan
              is a good answer to an unclear instruction.
            - When you are done, explain in two or three sentences what you are
              proposing and why. The administrator reads that before approving.
            """;

    private final Optional<AnthropicClient> client;
    private final String model;
    private final AgentToolCatalog catalog;
    private final AgentPlanStore planStore;
    private final Clock clock;

    public AdminAgentService(
            com.bienvenueblainville.assistant.AnthropicClientProvider anthropic,
            AgentToolCatalog catalog,
            AgentPlanStore planStore,
            com.bienvenueblainville.assistant.AssistantProperties properties
    ) {
        this.client = anthropic.client();
        this.model = properties.model();
        this.catalog = catalog;
        this.planStore = planStore;
        this.clock = Clock.systemDefaultZone();
    }

    // ------------------------------------------------------------------ plan

    public AgentPlanView plan(String instruction, Long adminUserId) {
        // 503 rather than a degraded imitation. Unlike the sorting assistant,
        // whose retrieval step already does the hard part and can answer
        // without a model, there is no honest non-model version of "read this
        // sentence and work out which rows to change". A rule-based stand-in
        // would be a different feature wearing this one's name, and it would
        // mislead exactly the person trying to evaluate whether the agent
        // works. The admin forms are unaffected, which is what the message
        // says.
        AnthropicClient anthropic = client.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The admin agent needs an Anthropic API key (ANTHROPIC_API_KEY) to be configured. "
                        + "Every action it offers is also available through the admin forms."));

        List<MessageParam> conversation = new ArrayList<>();
        conversation.add(MessageParam.builder()
                .role(MessageParam.Role.USER)
                .content("Today is " + LocalDate.now(clock) + ".\n\nAdministrator's instruction:\n" + instruction)
                .build());

        List<PlannedStep> steps = new ArrayList<>();
        String narrative = "";

        for (int turn = 0; turn < MAX_TURNS; turn++) {
            Message response = anthropic.messages().create(MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(16000L)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.HIGH).build())
                    .system(SYSTEM)
                    .tools(catalog.tools())
                    .messages(conversation)
                    .build());

            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The agent declined this request. Use the admin forms directly.");
            }

            narrative = joinText(response);
            conversation.add(response.toParam());

            List<ContentBlockParam> toolResults = new ArrayList<>();
            for (ContentBlock block : response.content()) {
                block.toolUse().ifPresent(use -> {
                    AgentToolName tool = AgentToolName.from(use.name());
                    Map<String, Object> input = readInput(use._input());

                    if (tool.isWrite()) {
                        // Recorded, not run. The model is told this, and is told
                        // to treat the call as final anyway.
                        steps.add(new PlannedStep(tool, input, StepSummariser.describe(tool, input)));
                        toolResults.add(toolResult(use.id(),
                                "Recorded as step " + steps.size() + " of the plan. Not executed yet - "
                                        + "a human administrator will approve it."));
                    } else {
                        toolResults.add(toolResult(use.id(), read(tool)));
                    }
                });
            }

            if (toolResults.isEmpty()) {
                // No tool calls left: the model has said its piece.
                return view(instruction, narrative, steps, adminUserId);
            }

            conversation.add(MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    .contentOfBlockParams(toolResults)
                    .build());
        }

        log.warn("Admin agent hit the {}-turn ceiling; returning whatever it had proposed", MAX_TURNS);
        return view(instruction, narrative, steps, adminUserId);
    }

    private AgentPlanView view(String instruction, String narrative, List<PlannedStep> steps, Long adminUserId) {
        List<AgentPlanView.AgentStepView> stepViews = steps.stream()
                .map(step -> new AgentPlanView.AgentStepView(step.tool().name(), step.summary()))
                .toList();

        if (steps.isEmpty()) {
            // No id at all, so the UI has nothing to confirm. An empty plan is a
            // legitimate outcome - the model is told to return one when the
            // instruction is unclear.
            return new AgentPlanView(null, instruction, narrative, stepViews);
        }

        String planId = planStore.save(new AgentPlan(
                AgentPlanStore.newPlanId(), adminUserId, instruction, narrative, steps, Instant.now(clock)));

        return new AgentPlanView(planId, instruction, narrative, stepViews);
    }

    // --------------------------------------------------------------- execute

    /**
     * Runs an approved plan, in order, stopping at the first failure.
     *
     * <p>Not transactional, and the response says so rather than hiding it.
     * Wrapping the whole plan in one transaction would be tidier but would also
     * mean an administrator watching "5 of 7 succeeded" has no idea which 5 —
     * reporting exactly how far it got is more useful than an all-or-nothing
     * promise these endpoints were never written to make.
     */
    public AgentExecutionView execute(String planId, Long adminUserId) {
        AgentPlan plan = planStore.consume(planId, adminUserId);

        List<String> results = new ArrayList<>();
        for (PlannedStep step : plan.steps()) {
            try {
                results.add(catalog.executeWrite(step));
            } catch (RuntimeException e) {
                log.warn("Admin agent plan {} failed at step {}", planId, results.size() + 1, e);
                return new AgentExecutionView(results.size(), plan.steps().size(), results,
                        "Step %d (%s) failed: %s".formatted(
                                results.size() + 1, step.summary(), rootMessage(e)));
            }
        }

        return new AgentExecutionView(results.size(), plan.steps().size(), results, null);
    }

    // ----------------------------------------------------------------- utils

    private String read(AgentToolName tool) {
        try {
            return catalog.executeRead(tool);
        } catch (Exception e) {
            log.warn("Admin agent read tool {} failed", tool, e);
            // Handed back as a tool result rather than thrown: the model can
            // recover from "that lookup failed" by saying so in its narrative,
            // which is more useful to the administrator than a 500.
            return "That lookup failed: " + rootMessage(e);
        }
    }

    private Map<String, Object> readInput(com.anthropic.core.JsonValue rawInput) {
        try {
            // JsonValue.convert, not toString-then-parse: toString() is a debug
            // representation, not JSON, so parsing it works for an empty object
            // and fails the moment a tool call carries real arguments. Also
            // never string-matched - escaping in tool inputs varies between
            // models, so this is always a structured conversion.
            return rawInput.convert(new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The agent produced arguments that could not be read.");
        }
    }

    private static ContentBlockParam toolResult(String toolUseId, String content) {
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(toolUseId)
                .content(content)
                .build());
    }

    private static String joinText(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("")
                .trim();
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
