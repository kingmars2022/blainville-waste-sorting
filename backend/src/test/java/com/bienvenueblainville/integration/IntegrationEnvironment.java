package com.bienvenueblainville.integration;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.util.Optional;

/**
 * Where the integration suite finds its infrastructure.
 *
 * <p>Shared by every {@code @Tag("integration")} test so the suite cannot end
 * up with two classes disagreeing about which database or Redis they run
 * against — which is exactly the kind of drift that makes a green local run
 * and a red CI run for no visible reason.
 *
 * <p>Defaults target the Docker Compose stack from the README
 * ({@code docker compose up -d mysql redis}); CI overrides them with
 * environment variables pointing at its service containers.
 */
public final class IntegrationEnvironment {
    public static final String ADMIN_EMAIL = "admin@blainville.local";
    public static final String ADMIN_PASSWORD = "IntegrationTestAdminPass123!";

    private IntegrationEnvironment() {
    }

    public static void register(DynamicPropertyRegistry registry) {
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
        registry.add("REDIS_HOST", () -> env("REDIS_HOST", "localhost"));
        registry.add("REDIS_PORT", () -> env("REDIS_PORT", "6379"));
        // Off unless a test asks for it. Kafka is the one dependency here that
        // cannot be shared between test classes cheaply, so the suites that do
        // not exercise events should not pay to start a broker - nor sit in a
        // reconnect loop against one that is not there.
        registry.add("app.events.enabled", () -> "false");
    }

    static String env(String name, String fallback) {
        return Optional.ofNullable(System.getenv(name))
                .filter(value -> !value.isBlank())
                .orElse(fallback);
    }
}
