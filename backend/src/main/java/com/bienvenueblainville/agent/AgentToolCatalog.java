package com.bienvenueblainville.agent;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolUnion;
import com.bienvenueblainville.collection.CollectionEvent;
import com.bienvenueblainville.collection.CollectionService;
import com.bienvenueblainville.collection.dto.CollectionEventRequest;
import com.bienvenueblainville.notice.SpecialNotice;
import com.bienvenueblainville.notice.SpecialNoticeService;
import com.bienvenueblainville.notice.dto.NoticeRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The agent's entire world: what it can see, what it can propose, and how each
 * of those maps onto code that already existed.
 *
 * <p>Every tool here calls the same {@code CollectionService} /
 * {@code SpecialNoticeService} method the admin forms call. Nothing bypasses
 * them, so the agent inherits their validation, their 404s, and their cache
 * eviction for free — and cannot acquire a capability an administrator does
 * not already have through the UI.
 */
@Component
public class AgentToolCatalog {
    private final CollectionService collections;
    private final SpecialNoticeService notices;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AgentToolCatalog(
            CollectionService collections,
            SpecialNoticeService notices,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.collections = collections;
        this.notices = notices;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * How many rows a read tool may put in front of the model.
     *
     * <p>Both listings used to be unbounded. Every row went into the prompt,
     * was paid for on every planning call, and - for the schedule - grew for
     * ever, because the calendar extends itself and nothing prunes the past.
     * The tool descriptions say what the bound is, so the model does not read
     * a short list as "there is nothing else".
     */
    private static final int PLANNING_LIMIT = 200;

    // ---------------------------------------------------------------- schemas

    public List<ToolUnion> tools() {
        return List.of(
                tool(AgentToolName.list_collections,
                        "List the upcoming collection schedule: every sector, from today forward, "
                                + "at most " + PLANNING_LIMIT + " rows. Collections that have already "
                                + "happened are NOT listed, because they cannot usefully be changed. "
                                + "Call this before proposing any change to the schedule, so that you "
                                + "are working from real rows and real ids rather than assuming.",
                        properties(), List.of()),

                tool(AgentToolName.list_notices,
                        "List public notices, including inactive and expired ones, newest first, at "
                                + "most " + PLANNING_LIMIT + " of them. Call this before changing or "
                                + "deleting a notice.",
                        properties(), List.of()),

                tool(AgentToolName.create_collection,
                        "Add one collection event to the schedule.",
                        collectionProperties(false), List.of("collectionDate", "sector", "collectionType", "binColor")),

                tool(AgentToolName.update_collection,
                        "Replace one existing collection event. Every field is overwritten, so pass "
                                + "the fields you want kept as well as the ones you are changing. Get "
                                + "the id from list_collections.",
                        collectionProperties(true), List.of("id", "collectionDate", "sector", "collectionType", "binColor")),

                tool(AgentToolName.delete_collection,
                        "Remove one collection event from the schedule. Get the id from list_collections.",
                        properties(entry("id", integerSchema("The collection event id to delete."))),
                        List.of("id")),

                tool(AgentToolName.create_notice,
                        "Publish a public notice. All three languages are required; write them "
                                + "yourself from the administrator's instruction.",
                        noticeProperties(false),
                        List.of("startsOn", "endsOn", "titleFr", "titleEn", "titleZh",
                                "bodyFr", "bodyEn", "bodyZh", "active")),

                tool(AgentToolName.update_notice,
                        "Replace one existing notice. Every field is overwritten. Get the id from list_notices.",
                        noticeProperties(true),
                        List.of("id", "startsOn", "endsOn", "titleFr", "titleEn", "titleZh",
                                "bodyFr", "bodyEn", "bodyZh", "active")),

                tool(AgentToolName.delete_notice,
                        "Delete one notice permanently. Prefer setting active=false through "
                                + "update_notice unless the administrator explicitly asked for deletion.",
                        properties(entry("id", integerSchema("The notice id to delete."))),
                        List.of("id")));
    }

    private ToolUnion tool(AgentToolName name, String description,
                           Tool.InputSchema.Properties properties, List<String> required) {
        return ToolUnion.ofTool(Tool.builder()
                .name(name.name())
                .description(description)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties)
                        .required(required)
                        .build())
                .build());
    }

    // ------------------------------------------------------------- dispatch

    /** Runs a read tool immediately; the model needs real data to plan against. */
    public String executeRead(AgentToolName tool) throws JsonProcessingException {
        return switch (tool) {
            case list_collections -> objectMapper.writeValueAsString(collections.forPlanning(PLANNING_LIMIT));
            case list_notices -> objectMapper.writeValueAsString(notices.recent(PLANNING_LIMIT));
            default -> throw new IllegalArgumentException(tool + " is not a read tool");
        };
    }

    /** Runs an approved write. Only ever reached from {@code AdminAgentService.execute}. */
    public String executeWrite(PlannedStep step) {
        return switch (step.tool()) {
            case create_collection -> {
                CollectionEvent created = collections.create(validated(step.input(), CollectionEventRequest.class));
                yield "Created collection event " + created.id();
            }
            case update_collection -> {
                Long id = requireId(step.input());
                collections.update(id, validated(withoutId(step.input()), CollectionEventRequest.class));
                yield "Updated collection event " + id;
            }
            case delete_collection -> {
                Long id = requireId(step.input());
                collections.delete(id);
                yield "Deleted collection event " + id;
            }
            case create_notice -> {
                SpecialNotice created = notices.create(validated(step.input(), NoticeRequest.class));
                yield "Created notice " + created.id();
            }
            case update_notice -> {
                Long id = requireId(step.input());
                notices.update(id, validated(withoutId(step.input()), NoticeRequest.class));
                yield "Updated notice " + id;
            }
            case delete_notice -> {
                Long id = requireId(step.input());
                notices.delete(id);
                yield "Deleted notice " + id;
            }
            default -> throw new IllegalArgumentException(step.tool() + " is not a write tool");
        };
    }

    /**
     * Binds the model's arguments to the same request record the REST controller
     * uses, and then runs the same bean validation on it.
     *
     * <p>That second half is easy to forget and would be a real hole: the
     * {@code @NotNull} and {@code @NotBlank} annotations on these records are
     * applied by Spring at the controller boundary, and the agent does not go
     * through that boundary. Without an explicit call, a plan with a missing
     * required field would reach MyBatis instead of being rejected.
     */
    private <T> T validated(Map<String, Object> input, Class<T> type) {
        T request = objectMapper.convertValue(input, type);

        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Invalid arguments: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", ")));
        }
        return request;
    }

    private static Long requireId(Map<String, Object> input) {
        Object id = input.get("id");
        if (!(id instanceof Number number)) {
            throw new IllegalArgumentException("This step needs a numeric id");
        }
        return number.longValue();
    }

    private static Map<String, Object> withoutId(Map<String, Object> input) {
        Map<String, Object> copy = new LinkedHashMap<>(input);
        copy.remove("id");
        return copy;
    }

    // --------------------------------------------------------- schema helpers

    private static Tool.InputSchema.Properties properties(Map.Entry<String, JsonValue>... entries) {
        Tool.InputSchema.Properties.Builder builder = Tool.InputSchema.Properties.builder();
        for (Map.Entry<String, JsonValue> entry : entries) {
            builder.putAdditionalProperty(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static Map.Entry<String, JsonValue> entry(String name, JsonValue schema) {
        return Map.entry(name, schema);
    }

    private static Tool.InputSchema.Properties collectionProperties(boolean withId) {
        Tool.InputSchema.Properties.Builder builder = Tool.InputSchema.Properties.builder();
        if (withId) {
            builder.putAdditionalProperty("id", integerSchema("The existing collection event id."));
        }
        builder.putAdditionalProperty("collectionDate", stringSchema("Collection date, ISO 8601 (YYYY-MM-DD)."));
        builder.putAdditionalProperty("sector", enumSchema("Which half of the city.", "north", "south", "all"));
        builder.putAdditionalProperty("collectionType",
                enumSchema("What is collected.", "organic", "recycling", "garbage", "bulky", "ecocentre", "special"));
        builder.putAdditionalProperty("binColor", enumSchema("Bin colour.", "brown", "blue", "black", "none"));
        builder.putAdditionalProperty("noteFr", stringSchema("Optional note in French."));
        builder.putAdditionalProperty("noteEn", stringSchema("Optional note in English."));
        builder.putAdditionalProperty("noteZh", stringSchema("Optional note in Chinese."));
        builder.putAdditionalProperty("sourceUrl", stringSchema("Optional official source URL."));
        return builder.build();
    }

    private static Tool.InputSchema.Properties noticeProperties(boolean withId) {
        Tool.InputSchema.Properties.Builder builder = Tool.InputSchema.Properties.builder();
        if (withId) {
            builder.putAdditionalProperty("id", integerSchema("The existing notice id."));
        }
        builder.putAdditionalProperty("startsOn", stringSchema("First day the notice is visible, ISO 8601."));
        builder.putAdditionalProperty("endsOn", stringSchema("Last day the notice is visible, ISO 8601. Must not be before startsOn."));
        builder.putAdditionalProperty("titleFr", stringSchema("Title in French."));
        builder.putAdditionalProperty("titleEn", stringSchema("Title in English."));
        builder.putAdditionalProperty("titleZh", stringSchema("Title in Chinese."));
        builder.putAdditionalProperty("bodyFr", stringSchema("Body in French."));
        builder.putAdditionalProperty("bodyEn", stringSchema("Body in English."));
        builder.putAdditionalProperty("bodyZh", stringSchema("Body in Chinese."));
        builder.putAdditionalProperty("sourceUrl", stringSchema("Optional official source URL."));
        builder.putAdditionalProperty("active", booleanSchema("Whether residents see it at all."));
        return builder.build();
    }

    private static JsonValue stringSchema(String description) {
        return JsonValue.from(Map.of("type", "string", "description", description));
    }

    private static JsonValue integerSchema(String description) {
        return JsonValue.from(Map.of("type", "integer", "description", description));
    }

    private static JsonValue booleanSchema(String description) {
        return JsonValue.from(Map.of("type", "boolean", "description", description));
    }

    private static JsonValue enumSchema(String description, String... values) {
        return JsonValue.from(Map.of("type", "string", "description", description, "enum", List.of(values)));
    }
}
