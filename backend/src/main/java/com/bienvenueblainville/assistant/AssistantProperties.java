package com.bienvenueblainville.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param provider      {@code template} (default, no API key, no cost) or {@code anthropic}
 * @param model         Claude model id, used only when provider is {@code anthropic}
 * @param apiKey        read from ANTHROPIC_API_KEY; never committed, never logged
 * @param rateLimit     per-IP quota for the public endpoint
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(
        String provider,
        String model,
        String apiKey,
        RateLimit rateLimit
) {
    public record RateLimit(int requestsPerHour) {
    }
}
