package com.bienvenueblainville.assistant;

import com.anthropic.client.AnthropicClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

import java.util.Optional;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfig {
    private static final Logger log = LoggerFactory.getLogger(AssistantConfig.class);

    /**
     * Picks the composer, and degrades instead of refusing to start.
     *
     * <p>A missing API key is a configuration gap, not a corrupt deployment:
     * the assistant still answers from the guide, so failing the whole
     * application's startup over it would take down the collection schedule
     * and the admin console too. It logs loudly at WARN so the gap is visible
     * rather than silent.
     */
    @Bean
    public AnswerComposer answerComposer(AssistantProperties properties, AnthropicClientProvider anthropic) {
        AnswerComposer template = new TemplateAnswerComposer();

        if (!"anthropic".equalsIgnoreCase(properties.provider())) {
            log.info("Assistant provider: template (set app.assistant.provider=anthropic to use Claude)");
            return template;
        }

        Optional<AnthropicClient> client = anthropic.client();
        if (client.isEmpty()) {
            log.warn("app.assistant.provider=anthropic but ANTHROPIC_API_KEY is not set; "
                    + "falling back to the template composer");
            return template;
        }

        log.info("Assistant provider: Claude ({})", properties.model());
        // A resident is waiting on this request, and a slow answer is worse
        // than the template's instant one - so this caller gets a short
        // deadline and no retries, while the admin agent keeps the provider's
        // longer default for its multi-turn planning loop.
        AnthropicClient impatient = client.get().withOptions(options -> options
                .timeout(Duration.ofSeconds(15))
                .maxRetries(0));
        return new ClaudeAnswerComposer(impatient, properties.model(), template);
    }
}
