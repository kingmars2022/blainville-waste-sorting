package com.bienvenueblainville.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public AnswerComposer answerComposer(AssistantProperties properties) {
        AnswerComposer template = new TemplateAnswerComposer();

        if (!"anthropic".equalsIgnoreCase(properties.provider())) {
            log.info("Assistant provider: template (set app.assistant.provider=anthropic to use Claude)");
            return template;
        }

        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("app.assistant.provider=anthropic but ANTHROPIC_API_KEY is not set; "
                    + "falling back to the template composer");
            return template;
        }

        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        log.info("Assistant provider: Claude ({})", properties.model());
        return new ClaudeAnswerComposer(client, properties.model(), template);
    }
}
