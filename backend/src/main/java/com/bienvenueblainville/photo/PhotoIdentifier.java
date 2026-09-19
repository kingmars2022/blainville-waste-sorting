package com.bienvenueblainville.photo;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.bienvenueblainville.assistant.AnthropicClientProvider;
import com.bienvenueblainville.common.LanguageCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Names what is in a photo. Nothing more.
 *
 * <p>The prompt asks for an object name and forbids a disposal rule, and the
 * response schema has nowhere to put one — so the constraint is structural
 * rather than a request the model might drift from. What comes out of here is
 * a search term, which {@link PhotoSortingService} then looks up in the
 * municipal guide exactly as if the resident had typed it.
 */
@Component
public class PhotoIdentifier {
    private static final Logger log = LoggerFactory.getLogger(PhotoIdentifier.class);

    private static final String SYSTEM = """
            You identify objects in photographs taken by residents of Blainville,
            Quebec, who want to know how to dispose of them.

            Name the object. That is your whole job.

            Do NOT say which bin it belongs in, where to take it, or how to
            dispose of it - you are not given the municipal sorting rules, and
            the application looks them up itself from the name you provide.
            Guessing a bin colour would produce a confident answer that a
            resident has no way to check.

            Be concrete and ordinary: name it the way a resident would say it,
            not the way a materials scientist would. "boîte à pizza", not
            "corrugated fibreboard food container".

            If the photo is too blurry, too dark, or shows several unrelated
            things, set uncertain to true rather than picking one at random.
            """;

    private final Optional<AnthropicClient> client;
    private final String model;

    public PhotoIdentifier(AnthropicClientProvider anthropic,
                           com.bienvenueblainville.assistant.AssistantProperties properties) {
        // A resident is watching a spinner, and a vision call on a 1024px JPEG
        // is not a long job. Fail over to "we could not identify it" rather
        // than holding the request open.
        this.client = anthropic.client().map(c -> c.withOptions(options -> options
                .timeout(Duration.ofSeconds(25))
                .maxRetries(0)));
        this.model = properties.model();
    }

    public boolean isConfigured() {
        return client.isPresent();
    }

    public Optional<MaterialIdentification> identify(byte[] jpeg, LanguageCode language) {
        AnthropicClient anthropic = client.orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Photo identification needs an Anthropic API key (ANTHROPIC_API_KEY) to be configured. "
                        + "Searching the sorting guide by name works without it."));

        try {
            StructuredMessageCreateParams<MaterialIdentification> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(4096L)
                    .system(SYSTEM)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofImage(ImageBlockParam.builder()
                                    .source(Base64ImageSource.builder()
                                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                            .data(Base64.getEncoder().encodeToString(jpeg))
                                            .build())
                                    .build()),
                            ContentBlockParam.ofText(TextBlockParam.builder()
                                    .text("Name this object in " + languageName(language) + ".")
                                    .build())))
                    // The schema is the constraint that matters more than a cheaper
                    // effort setting would be: the response has nowhere to put a
                    // bin colour, so the model cannot drift into deciding one.
                    // OutputConfig carries effort and format in the same object
                    // and this overload sets it wholesale, so effort stays at its
                    // default rather than being silently dropped by ordering.
                    .outputConfig(MaterialIdentification.class)
                    .build();

            var response = anthropic.messages().create(params);

            if (response.stopReason().filter(StopReason.END_TURN::equals).isEmpty()) {
                // Covers a safety refusal and a truncated response alike. A
                // half-parsed identification is worse than none, because the
                // search that follows would be confidently wrong.
                log.warn("Vision call did not complete normally; treating the photo as unidentified");
                return Optional.empty();
            }

            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(text -> text.text())
                    .filter(identification -> identification != null && !identification.uncertain())
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn("Vision call failed; treating the photo as unidentified", e);
            return Optional.empty();
        }
    }

    private static String languageName(LanguageCode language) {
        return switch (language) {
            case fr -> "French";
            case en -> "English";
            case zh -> "Chinese";
        };
    }
}
