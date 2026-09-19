package com.bienvenueblainville.audit;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

/**
 * Audit trail in MongoDB.
 *
 * <p>The one place the document model genuinely reads better than a relational
 * one: {@code before} and {@code after} are whole entity snapshots whose shape
 * depends on the entity type, and they go in as they are — no column per
 * field, no serialization step the reader has to undo.
 *
 * <p>Idempotency is an upsert on the event id rather than a read-then-write,
 * so two consumers replaying the same event concurrently cannot both decide it
 * is new.
 */
public class MongoAuditStore implements AuditStore {
    static final String COLLECTION = "audit_records";

    private final MongoTemplate mongo;

    public MongoAuditStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void record(AuditRecord entry) {
        mongo.upsert(
                Query.query(Criteria.where("_id").is(entry.eventId())),
                new Update()
                        .setOnInsert("entityType", entry.entityType())
                        .setOnInsert("entityId", entry.entityId())
                        .setOnInsert("action", entry.action())
                        .setOnInsert("actor", entry.actor())
                        .setOnInsert("occurredAt", entry.occurredAt())
                        .setOnInsert("before", entry.before())
                        .setOnInsert("after", entry.after()),
                COLLECTION);
    }

    @Override
    public List<AuditRecord> findByEntity(String entityType, Long entityId) {
        Query query = Query.query(Criteria.where("entityType").is(entityType).and("entityId").is(entityId))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "occurredAt"));

        return mongo.find(query, org.bson.Document.class, COLLECTION).stream()
                .map(MongoAuditStore::toRecord)
                .toList();
    }

    @Override
    public long count() {
        return mongo.getCollection(COLLECTION).countDocuments();
    }

    @Override
    public String backendName() {
        return "mongodb";
    }

    private static AuditRecord toRecord(org.bson.Document document) {
        java.util.Date occurred = document.getDate("occurredAt");
        return new AuditRecord(
                document.getString("_id"),
                document.getString("entityType"),
                document.get("entityId") == null ? null : ((Number) document.get("entityId")).longValue(),
                document.getString("action"),
                document.getString("actor"),
                occurred == null ? null : occurred.toInstant(),
                document.get("before"),
                document.get("after"));
    }
}
