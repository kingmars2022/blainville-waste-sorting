package com.bienvenueblainville.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Composes the answer with Claude, strictly over the entries retrieval already
 * found.
 *
 * <p>The model's job here is narrow and deliberately so: phrase, translate and
 * condense facts it is handed. It is never asked what the sorting rule is, so
 * the failure mode that matters for a municipal service — a confident wrong
 * answer about which bin something goes in — is designed out rather than
 * prompted against. Grounding is enforced upstream: if retrieval finds
 * nothing, {@link AssistantService} refuses without calling this class at all.
 *
 * <p>Any failure falls back to {@link TemplateAnswerComposer}. An outage at
 * Anthropic, an expired key, a rate limit, or a safety refusal must degrade
 * the sorting guide to "plainer wording", never to an error page — the same
 * principle the Redis cache follows.
 */
public class ClaudeAnswerComposer implements AnswerComposer {
    private static final Logger log = LoggerFactory.getLogger(ClaudeAnswerComposer.class);

    private static final String SYSTEM = """
            You answer waste-sorting questions for residents of Blainville, Quebec.

            You will be given the resident's question and the entries retrieved
            from the municipal sorting guide. Those entries are the only facts
            you may use.

            Rules:
            - Never state a disposal rule, bin colour or location that is not in
              the entries provided. If they do not cover the question, say so
              plainly and suggest they check blainville.ca.
            - Answer in the same language as the question.
            - Be brief: two or three sentences. Residents are usually standing
              at their bin.
            - Name the bin or destination explicitly, because that is the part
              they actually need.
            - Do not invent collection dates. You are not given the schedule.
            """;

    private final AnthropicClient client;
    private final String model;
    private final AnswerComposer fallback;

    public ClaudeAnswerComposer(AnthropicClient client, String model, AnswerComposer fallback) {
        this.client = client;
        this.model = model;
        this.fallback = fallback;
    }

    @Override
    public String providerName() {
        return "anthropic:" + model;
    }

    @Override
    public String compose(AssistantQuestion question, List<RetrievedItem> context) {
        try {
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(model)
                    // Short, factual, fully grounded output - the cheap end of
                    // the effort range is the right one, not a compromise.
                    .outputConfig(OutputConfig.builder()
                            .effort(OutputConfig.Effort.LOW)
                            .build())
                    // Room for adaptive thinking plus a three-sentence answer.
                    // Sized low because the output genuinely is short, not to
                    // save money at the cost of truncating mid-sentence.
                    .maxTokens(4096L)
                    .system(SYSTEM)
                    .addUserMessage(userPrompt(question, context))
                    .build();

            Message response = client.messages().create(params);

            // A safety refusal returns HTTP 200 with no usable text, so
            // stop_reason has to be checked before reading content.
            if (response.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
                log.warn("Claude declined the assistant request; falling back to the guide text verbatim");
                return fallback.compose(question, context);
            }

            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(block -> block.text())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            if (text.isBlank()) {
                log.warn("Claude returned no text for the assistant request; falling back");
                return fallback.compose(question, context);
            }
            return text.trim();
        } catch (RuntimeException e) {
            log.warn("Claude call failed; falling back to the guide text verbatim", e);
            return fallback.compose(question, context);
        }
    }

    private String userPrompt(AssistantQuestion question, List<RetrievedItem> context) {
        StringBuilder prompt = new StringBuilder()
                .append("Resident's question (language: ").append(question.language()).append("):\n")
                .append(question.text())
                .append("\n\nSorting guide entries retrieved for this question:\n");

        for (RetrievedItem item : context) {
            prompt.append("\n- Item: ").append(item.name())
                    .append("\n  Destination: ").append(item.destinationType())
                    .append("\n  Bin: ").append(item.binColor())
                    .append("\n  Instruction: ").append(item.instruction());
            if (item.location() != null && !item.location().isBlank()) {
                prompt.append("\n  Location: ").append(item.location());
            }
            if (item.sourceUrl() != null && !item.sourceUrl().isBlank()) {
                prompt.append("\n  Source: ").append(item.sourceUrl());
            }
        }

        return prompt.toString();
    }
}
