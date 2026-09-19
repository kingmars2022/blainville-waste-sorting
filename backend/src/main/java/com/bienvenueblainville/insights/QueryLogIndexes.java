package com.bienvenueblainville.insights;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Creates the indexes the query log needs, once, at startup.
 *
 * <p>Auto-index creation is off for this application, so indexes are declared
 * where they can be read and reasoned about rather than inferred from
 * annotations. Two of them:
 *
 * <ul>
 *   <li>A <b>TTL index</b> on {@code occurredAt}. Resident questions expire
 *       after 180 days with no scheduled job and no cleanup script — a question
 *       from two years ago says nothing about what the guide is missing today,
 *       and keeping it is a liability rather than an asset.</li>
 *   <li>A compound index on {@code grounded} and {@code occurredAt}, which is
 *       exactly the shape of the gap-report query. Without it that aggregation
 *       is a collection scan that grows with traffic.</li>
 * </ul>
 */
@Component
public class QueryLogIndexes {
    private static final Logger log = LoggerFactory.getLogger(QueryLogIndexes.class);

    private final ObjectProvider<MongoTemplate> mongo;

    public QueryLogIndexes(ObjectProvider<MongoTemplate> mongo) {
        this.mongo = mongo;
    }

    /**
     * Runs on its own thread, after startup.
     *
     * <p>Not an optimisation — a correctness issue found by a test getting
     * slower. MongoDB's driver blocks for its server-selection timeout (30
     * seconds by default) when nothing is listening, and this listener was
     * doing that on the startup thread. An application whose MongoDB is
     * missing or slow would take half a minute longer to become ready, for
     * indexes that only make a monthly report faster. Optional infrastructure
     * must not be able to delay startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        MongoTemplate template = mongo.getIfAvailable();
        if (template == null) {
            return;
        }

        Thread worker = new Thread(() -> create(template), "query-log-indexes");
        worker.setDaemon(true);
        worker.start();
    }

    private void create(MongoTemplate template) {
        try {
            var collection = template.getCollection(QueryLogStore.COLLECTION);

            collection.createIndex(
                    new org.bson.Document("occurredAt", 1),
                    new com.mongodb.client.model.IndexOptions()
                            .expireAfter(QueryLogStore.RETENTION.toSeconds(), TimeUnit.SECONDS));

            collection.createIndex(new org.bson.Document("grounded", 1).append("occurredAt", -1));
        } catch (RuntimeException e) {
            // Analytics indexes are not worth failing a deployment over. The
            // reports still work without them, just slower.
            log.warn("Could not create the resident-query indexes", e);
        }
    }
}
