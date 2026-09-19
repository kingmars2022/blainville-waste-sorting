package com.bienvenueblainville.insights;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import org.bson.Document;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resident questions, and what the guide did with them.
 *
 * <p><b>This is the workload that actually wants a document store</b>, as
 * opposed to the audit trail, where MySQL's JSON column does the job just as
 * well and is therefore the default. Four differences matter:
 *
 * <ul>
 *   <li><b>Shape varies by source.</b> A typed question and a photo
 *       identification carry different fields, and a third source later would
 *       carry different ones again. Here that is a document; relationally it is
 *       a table of mostly-null columns or a join nobody wants.</li>
 *   <li><b>The reads are aggregations, not lookups.</b> "Which terms did
 *       residents ask about that we could not answer, grouped and ranked, with
 *       a sample of how they phrased it" is one pipeline below. In SQL it is a
 *       GROUP BY with a correlated sample, which is doable and considerably
 *       less pleasant.</li>
 *   <li><b>It is append-only and write-heavy.</b> Every question writes;
 *       nothing updates. There are no foreign keys to maintain and no
 *       transaction to join.</li>
 *   <li><b>It expires.</b> A TTL index drops documents after 180 days without
 *       a scheduled job, because a question from two years ago says nothing
 *       about what the guide is missing today.</li>
 * </ul>
 */
@Component
public class QueryLogStore {
    static final String COLLECTION = "resident_queries";

    /** Long enough to see a season, short enough that the collection stays small. */
    static final Duration RETENTION = Duration.ofDays(180);

    private final MongoTemplate mongo;

    public QueryLogStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void record(QueryEvent event) {
        Document document = new Document()
                .append("_id", event.queryId())
                .append("source", event.source())
                .append("language", event.language().name())
                .append("askedAbout", event.askedAbout())
                .append("searchedFor", event.searchedFor())
                .append("grounded", event.grounded())
                .append("topScore", event.topScore())
                .append("matchedNames", event.matchedNames())
                // Normalized once, on write, so the aggregation groups on it
                // without a per-document transformation at read time.
                .append("term", normalize(event.askedAbout()))
                .append("occurredAt", java.util.Date.from(event.occurredAt()));

        // Idempotent on the query id: the consumer is at-least-once like every
        // other one here, and a redelivered question must not count twice.
        mongo.getCollection(COLLECTION).replaceOne(
                new Document("_id", event.queryId()),
                document,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
    }

    /**
     * What residents asked that the guide could not answer.
     *
     * <p>The output of this is a work list: each row is an entry somebody
     * should consider writing.
     */
    public List<GuideGap> unansweredTerms(int days, int limit) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        Aggregation pipeline = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("grounded").is(false)
                        .and("occurredAt").gte(java.util.Date.from(since))),
                Aggregation.group("term")
                        .count().as("asks")
                        .addToSet("language").as("languages")
                        // The raw questions, so whoever writes the entry can see
                        // the words residents actually use - which is what the
                        // keyword table needs to contain.
                        .addToSet("askedAbout").as("examples"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "asks"),
                Aggregation.limit(limit));

        AggregationResults<Document> results =
                mongo.aggregate(pipeline, COLLECTION, Document.class);

        List<GuideGap> gaps = new ArrayList<>();
        for (Document row : results) {
            gaps.add(new GuideGap(
                    row.getString("_id"),
                    ((Number) row.get("asks")).longValue(),
                    strings(row.get("languages")),
                    strings(row.get("examples")).stream().limit(3).toList()));
        }
        return gaps;
    }

    /** Headline numbers for the admin console: volume, and how much of it failed. */
    public Map<String, Object> summary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        Aggregation pipeline = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("occurredAt").gte(java.util.Date.from(since))),
                Aggregation.group("grounded").count().as("count"));

        long answered = 0;
        long unanswered = 0;
        for (Document row : mongo.aggregate(pipeline, COLLECTION, Document.class)) {
            long count = ((Number) row.get("count")).longValue();
            if (Boolean.TRUE.equals(row.get("_id"))) {
                answered = count;
            } else {
                unanswered = count;
            }
        }

        long total = answered + unanswered;
        return Map.of(
                "days", days,
                "questions", total,
                "answered", answered,
                "unanswered", unanswered,
                // The number an administrator should watch. A rising figure
                // means the guide is drifting behind what residents own.
                "unansweredRate", total == 0 ? 0.0 : Math.round(unanswered * 1000d / total) / 10d);
    }

    public long count() {
        return mongo.getCollection(COLLECTION).countDocuments();
    }

    /**
     * Lowercased and trimmed, so "Aquarium", "aquarium " and "AQUARIUM" are one
     * row in the report rather than three.
     */
    static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return value instanceof List<?> list
                ? (List<String>) list.stream().map(String::valueOf).toList()
                : List.of();
    }
}
