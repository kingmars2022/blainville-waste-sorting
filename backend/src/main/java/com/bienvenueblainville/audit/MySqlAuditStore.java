package com.bienvenueblainville.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;

/**
 * The same audit trail in MySQL, using its native JSON column.
 *
 * <p>Kept alongside {@link MongoAuditStore} as the honest control. Side by
 * side, the two are about the same length and the same shape — which is the
 * point being made: at this scale the document store is a preference, not a
 * requirement, and the trail can live in the database the application already
 * runs without adding a system to operate.
 *
 * <p>Idempotency here is {@code INSERT ... ON DUPLICATE KEY UPDATE id = id} on
 * the event id: a no-op write that costs one round trip and cannot race, which
 * a SELECT-then-INSERT can.
 */
public class MySqlAuditStore implements AuditStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MySqlAuditStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditRecord entry) {
        jdbc.update("""
                        insert into audit_record
                            (event_id, entity_type, entity_id, action, actor, occurred_at, before_state, after_state)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        on duplicate key update event_id = event_id
                        """,
                entry.eventId(), entry.entityType(), entry.entityId(), entry.action(), entry.actor(),
                Timestamp.from(entry.occurredAt()), json(entry.before()), json(entry.after()));
    }

    @Override
    public List<AuditRecord> findByEntity(String entityType, Long entityId) {
        return jdbc.query("""
                        select event_id, entity_type, entity_id, action, actor, occurred_at,
                               before_state, after_state
                        from audit_record
                        where entity_type = ? and entity_id = ?
                        order by occurred_at, id
                        """,
                (rs, rowNum) -> new AuditRecord(
                        rs.getString("event_id"),
                        rs.getString("entity_type"),
                        rs.getLong("entity_id"),
                        rs.getString("action"),
                        rs.getString("actor"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        parse(rs.getString("before_state")),
                        parse(rs.getString("after_state"))),
                entityType, entityId);
    }

    @Override
    public long count() {
        Long count = jdbc.queryForObject("select count(*) from audit_record", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public String backendName() {
        return "mysql-json";
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize an audit snapshot", e);
        }
    }

    private Object parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read back an audit snapshot", e);
        }
    }
}
