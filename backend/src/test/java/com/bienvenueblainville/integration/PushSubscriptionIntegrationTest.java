package com.bienvenueblainville.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registering a device, against the real schema.
 *
 * <p>The subscription endpoint takes three values straight from a caller and
 * stores a URL this server will later POST to. Most of what is worth testing
 * here is what it refuses.
 *
 * <p>Run with: {@code mvn test -Pintegration-test}
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class PushSubscriptionIntegrationTest {

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        IntegrationEnvironment.register(registry);
    }

    private static final String VALID_P256DH =
            "BAIX5hfwtkQ5KCePlpmeaaI6TywVK99tbN9m5bgCgtTtGUp968uXcS0t2jyoWqh2Wlb0X8dYWZZS8ol8ZTBuV5Q";
    private static final String ENDPOINT = "https://push.example/wpush/v2/integration-test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeTestSubscriptions() {
        jdbc.update("delete from push_subscription where endpoint like 'https://push.example/%'");
    }

    private Map<String, Object> request() {
        Map<String, Object> body = new HashMap<>();
        body.put("endpoint", ENDPOINT);
        body.put("p256dh", VALID_P256DH);
        body.put("auth", "RERERERERERERERERERERA");
        body.put("languageCode", "zh");
        return body;
    }

    @Test
    void thePublicKeyIsPublicAndSaysWhetherPushIsOn() throws Exception {
        mockMvc.perform(get("/api/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").exists())
                .andExpect(jsonPath("$.publicKey").exists());
    }

    @Test
    void subscribingRequiresBeingSignedIn() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void storesTheDeviceAndItsLanguage() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "select language_code from push_subscription where endpoint = ?", String.class, ENDPOINT))
                .isEqualTo("zh");
    }

    @Test
    void resubscribingTheSameDeviceUpdatesItRatherThanAddingAnother() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request()))).andExpect(status().isNoContent());

        Map<String, Object> again = request();
        again.put("languageCode", "en");
        mockMvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(again))).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject(
                "select count(*) from push_subscription where endpoint = ?", Integer.class, ENDPOINT)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select language_code from push_subscription where endpoint = ?", String.class, ENDPOINT))
                .isEqualTo("en");
    }

    @Test
    void refusesAnEndpointThatIsNotHttps() throws Exception {
        // The server POSTs to this URL on its own schedule. An unvalidated one
        // turns the notice pipeline into a request forgery primitive.
        Map<String, Object> body = request();
        body.put("endpoint", "http://169.254.169.254/latest/meta-data/");

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAKeyThatIsNotAPointOnTheCurve() throws Exception {
        // An off-curve point is something a caller can choose, and running
        // ECDH against one is the invalid-curve attack. Refused by the request
        // that supplied it rather than months later by a notice.
        Map<String, Object> body = request();
        // 65 bytes and correctly shaped, but (0, 0) is not on P-256.
        body.put("p256dh", "BAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        mockMvc.perform(post("/api/push/subscriptions")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsubscribingOnlyRemovesTheCallersOwnDevice() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/push/subscriptions").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request()))).andExpect(status().isNoContent());

        // Somebody else's device, which this caller must not be able to remove.
        // queryForObject throws on an empty result rather than returning null,
        // and a fresh database has only the seeded administrator.
        Long otherUserId = jdbc.queryForList(
                        "select id from app_user where email <> ? order by id asc limit 1",
                        Long.class, IntegrationEnvironment.ADMIN_EMAIL)
                .stream().findFirst().orElseGet(this::registerAResident);
        jdbc.update("insert into push_subscription (user_id, endpoint, p256dh, auth, language_code) "
                + "values (?, 'https://push.example/someone-else', ?, 'x', 'fr')", otherUserId, VALID_P256DH);

        mockMvc.perform(delete("/api/push/subscriptions")
                        .param("endpoint", "https://push.example/someone-else")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from push_subscription where endpoint = ?",
                Integer.class, "https://push.example/someone-else")).isEqualTo(1);

        mockMvc.perform(delete("/api/push/subscriptions")
                        .param("endpoint", ENDPOINT)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from push_subscription where endpoint = ?",
                Integer.class, ENDPOINT)).isZero();
    }

    private Long registerAResident() {
        try {
            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                            "email", "push-test@blainville.local",
                            "password", "PushTestPass123!"))));
            return jdbc.queryForObject("select id from app_user where email = ?",
                    Long.class, "push-test@blainville.local");
        } catch (Exception e) {
            throw new IllegalStateException("Could not create a second resident for the test", e);
        }
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", IntegrationEnvironment.ADMIN_EMAIL,
                                "password", IntegrationEnvironment.ADMIN_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }
}
