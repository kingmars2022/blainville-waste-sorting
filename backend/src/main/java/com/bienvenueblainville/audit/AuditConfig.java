package com.bienvenueblainville.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AuditConfig {
    private static final Logger log = LoggerFactory.getLogger(AuditConfig.class);

    /**
     * MySQL by default, because it is the store this application already
     * operates and, at this volume, the one that costs least to run. Set
     * {@code AUDIT_STORE=mongodb} to use the document store instead.
     *
     * <p>{@link ObjectProvider} rather than a direct injection so that a
     * deployment with no MongoDB configured still starts — the audit trail
     * falls back rather than taking the application down with it.
     */
    @Bean
    public AuditStore auditStore(
            @org.springframework.beans.factory.annotation.Value("${app.audit.store:mysql}") String store,
            JdbcTemplate jdbc,
            ObjectProvider<MongoTemplate> mongo,
            ObjectMapper objectMapper
    ) {
        if (!"mongodb".equalsIgnoreCase(store)) {
            log.info("Audit trail: MySQL JSON column");
            return new MySqlAuditStore(jdbc, objectMapper);
        }

        MongoTemplate template = mongo.getIfAvailable();
        if (template == null) {
            log.warn("app.audit.store=mongodb but no MongoTemplate is available; using MySQL instead");
            return new MySqlAuditStore(jdbc, objectMapper);
        }

        log.info("Audit trail: MongoDB");
        return new MongoAuditStore(template);
    }
}
