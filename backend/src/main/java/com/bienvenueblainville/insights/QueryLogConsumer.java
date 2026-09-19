package com.bienvenueblainville.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Moves resident questions off the request path and into MongoDB.
 *
 * <p>The broker is doing something here that a direct call could not: if
 * MongoDB is slow, full, or down, questions queue on the topic and residents
 * never notice. Writing the analytics row inline would put a second datastore
 * on the critical path of every question, to serve a monthly report.
 *
 * <p>It also means this consumer can be stopped, redeployed or replayed from
 * the start of the topic without touching the application serving residents.
 */
@Component
public class QueryLogConsumer {
    private static final Logger log = LoggerFactory.getLogger(QueryLogConsumer.class);

    private final QueryLogStore store;
    private final ObjectMapper objectMapper;

    public QueryLogConsumer(QueryLogStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = QueryEvent.TOPIC, groupId = "query-log",
            autoStartup = "${app.events.enabled:true}")
    public void consume(String payload) {
        try {
            store.record(objectMapper.readValue(payload, QueryEvent.class));
        } catch (Exception e) {
            // Skipped rather than retried forever: a payload this consumer
            // cannot read will not become readable, and blocking the partition
            // over an analytics row would be the tail wagging the dog.
            log.warn("Unreadable resident query event, skipping", e);
        }
    }
}
