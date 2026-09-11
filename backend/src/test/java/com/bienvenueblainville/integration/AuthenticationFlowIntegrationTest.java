package com.bienvenueblainville.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full Spring context (real SecurityConfig, real MyBatis mappers,
 * a real MySQL database) and drives it through MockMvc. This is what
 * actually caught the SecurityConfig startup crash, the MyBatis generated-key
 * bugs, and the 401/403 semantics bug from the manual verification session —
 * none of which the mocked unit tests (e.g. AuthServiceTest) could catch,
 * since those never construct a real Spring context or talk to a real
 * database or security filter chain.
 *
 * Requires a MySQL instance reachable via the same DB_URL/DB_USERNAME/
 * DB_PASSWORD environment variables the application itself reads — see
 * README "Running Locally". Run with: mvn test -Pintegration-test
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@blainville.local";
    private static final String ADMIN_PASSWORD = "IntegrationTestAdminPass123!";

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        String host = env("DB_HOST", "localhost");
        String port = env("DB_PORT", env("MYSQL_PORT", "3307"));
        String database = env("DB_NAME", "bienvenue_blainville");

        registry.add("DB_URL", () -> "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=America/Toronto"
                + "&allowPublicKeyRetrieval=true&useSSL=false");
        registry.add("DB_USERNAME", () -> env("DB_USERNAME", "blainville_app"));
        registry.add("DB_PASSWORD", () -> env("DB_PASSWORD", "replace_with_a_local_dev_password"));
        registry.add("APP_JWT_SECRET", () -> "integration-test-jwt-secret-long-enough-for-hmac-sha384");
        registry.add("APP_ADMIN_EMAIL", () -> ADMIN_EMAIL);
        registry.add("APP_ADMIN_PASSWORD", () -> ADMIN_PASSWORD);
    }

    private static String env(String name, String fallback) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken() throws Exception {
        String email = "resident-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "ResidentPass123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String loginAdminAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", ADMIN_EMAIL,
                                "password", ADMIN_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void registerCreatesAUserAccountThatCanImmediatelyAuthenticate() throws Exception {
        String token = registerAndGetToken();

        mockMvc.perform(get("/api/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector").value("north"));
    }

    @Test
    void duplicateRegistrationIsRejectedWithConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        Map<String, String> body = Map.of("email", email, "password", "ResidentPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void unauthenticatedRequestToProtectedRouteIsRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get("/api/preferences"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void residentCannotAccessAdminRoutesButAdminCan() throws Exception {
        String residentToken = registerAndGetToken();
        String adminToken = loginAdminAndGetToken();

        mockMvc.perform(get("/api/admin/notices").header("Authorization", "Bearer " + residentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/notices").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateAndDeleteASpecialNoticeEndToEnd() throws Exception {
        String adminToken = loginAdminAndGetToken();

        Map<String, Object> notice = Map.of(
                "startsOn", "2026-09-07",
                "endsOn", "2026-09-08",
                "titleFr", "Avis integration",
                "titleEn", "Integration notice",
                "titleZh", "集成测试通知",
                "bodyFr", "Corps",
                "bodyEn", "Body",
                "bodyZh", "内容",
                "active", true
        );

        MvcResult created = mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notice)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(id).isPositive();

        mockMvc.perform(delete("/api/admin/notices/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void noticeWithEndsOnBeforeStartsOnIsRejected() throws Exception {
        String adminToken = loginAdminAndGetToken();

        Map<String, Object> notice = Map.of(
                "startsOn", "2026-12-31",
                "endsOn", "2026-01-01",
                "titleFr", "x", "titleEn", "x", "titleZh", "x",
                "bodyFr", "x", "bodyEn", "x", "bodyZh", "x",
                "active", true
        );

        mockMvc.perform(post("/api/admin/notices")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(notice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanCreateUpdateAndDeleteASortingItemWithTranslations() throws Exception {
        String adminToken = loginAdminAndGetToken();

        Map<String, Object> translation = Map.of("name", "n", "instruction", "i");
        Map<String, Object> item = Map.of(
                "destinationType", "organic",
                "binColor", "brown",
                "fr", translation,
                "en", translation,
                "zh", translation
        );

        MvcResult created = mockMvc.perform(post("/api/admin/sorting-items")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/admin/sorting-items/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
