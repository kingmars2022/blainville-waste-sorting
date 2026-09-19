package com.bienvenueblainville.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * One Claude client for the whole application, or none.
 *
 * <p>An {@code Optional} rather than a bean that may or may not exist: "is
 * Claude configured?" is then an ordinary question any caller can ask, instead
 * of something encoded in whether an injection point resolved.
 *
 * <p>Gated on the API key alone, not on {@code app.assistant.provider}. That
 * setting chooses which composer answers residents' sorting questions; whether
 * the admin agent can run is a separate matter, and conflating them would mean
 * turning off Claude-worded sorting answers silently disabled the back office
 * agent too.
 */
@Component
public class AnthropicClientProvider {
    private static final Logger log = LoggerFactory.getLogger(AnthropicClientProvider.class);

    private final AnthropicClient client;

    public AnthropicClientProvider(AssistantProperties properties) {
        String apiKey = properties.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("No ANTHROPIC_API_KEY configured: the sorting assistant answers from the guide "
                    + "directly, and the admin agent is unavailable (the admin forms are not).");
            this.client = null;
        } else {
            this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        }
    }

    public Optional<AnthropicClient> client() {
        return Optional.ofNullable(client);
    }
}
