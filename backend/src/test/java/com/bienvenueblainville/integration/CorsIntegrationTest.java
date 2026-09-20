package com.bienvenueblainville.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Which browsers may talk to this API, driven through the real filter chain.
 *
 * <p>The allowed origin used to be a constant in {@link
 * com.bienvenueblainville.security.SecurityConfig}, which meant the
 * application could not be deployed anywhere without a code change — and the
 * symptom of getting it wrong is an opaque console error in someone else's
 * browser, not a log line here. Configuration alone would not be worth a test;
 * what is worth one is that the list is actually enforced, and that the one
 * response header the frontend reads survives the crossing.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

    private static final String ALLOWED = "https://blainville.example";
    private static final String ALSO_ALLOWED = "http://localhost:5173";

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
        // Two origins, with the untidy spacing a real .env file collects.
        registry.add("app.cors.allowed-origins", () -> ALLOWED + " , " + ALSO_ALLOWED);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsAPreflightFromAConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/sorting-items")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    void trimsWhitespaceRatherThanTreatingItAsPartOfTheOrigin() throws Exception {
        // " http://localhost:5173" is not an origin any browser will send, so
        // an untrimmed list silently stops matching the one it was written for.
        mockMvc.perform(options("/api/sorting-items")
                        .header("Origin", ALSO_ALLOWED)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALSO_ALLOWED));
    }

    @Test
    void refusesAnOriginThatIsNotOnTheList() throws Exception {
        mockMvc.perform(options("/api/sorting-items")
                        .header("Origin", "https://not-blainville.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void letsTheBrowserReadRetryAfter() throws Exception {
        // The photo retry waits out an unprocessed upload by reading
        // Retry-After. It is not a CORS-safelisted response header, so without
        // this the browser hides it from the script, `retryAfter` comes back
        // null, and the retry silently never happens - in a deployment, never
        // in development, where Vite proxies the API onto the same origin.
        mockMvc.perform(get("/api/sorting-items").header("Origin", ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    }
}
