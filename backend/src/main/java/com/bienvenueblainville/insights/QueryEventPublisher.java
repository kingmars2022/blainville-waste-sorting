package com.bienvenueblainville.insights;

import com.bienvenueblainville.common.LanguageCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes a question to the analytics stream, and is allowed to fail.
 *
 * <p><b>This is deliberately the opposite of the notice outbox, in the same
 * codebase, and the contrast is the point.</b> A notice event must never be
 * lost, so it is written inside the database transaction and relayed
 * afterwards. An analytics event must never cost a resident anything, so it is
 * fired and forgotten: no transaction, no retry, no waiting for an
 * acknowledgement, and every failure swallowed. Losing a handful of rows
 * slightly blurs a monthly report; making someone wait for Kafka before they
 * learn which bin to use is a worse outcome than never knowing they asked.
 *
 * <p>The two requirements are genuinely different, and picking one mechanism
 * for both would get one of them wrong.
 */
@Component
public class QueryEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(QueryEventPublisher.class);

    private final ObjectProvider<KafkaTemplate<String, String>> kafka;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;

    public QueryEventPublisher(
            ObjectProvider<KafkaTemplate<String, String>> kafka,
            ObjectMapper objectMapper,
            @Value("${app.events.enabled:true}") boolean enabled
    ) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
        this.enabled = enabled;
    }

    public void record(
            String source,
            LanguageCode language,
            String askedAbout,
            String searchedFor,
            boolean grounded,
            double topScore,
            List<String> matchedNames
    ) {
        // The same switch the relay and the consumers honour. Without this the
        // producer would keep trying to reach a broker that a Kafka-less
        // deployment never had, once per resident question - and the Kafka
        // client's own retry logging would be the loudest thing in the log.
        if (!enabled) {
            return;
        }

        KafkaTemplate<String, String> template = kafka.getIfAvailable();
        if (template == null) {
            return;
        }

        QueryEvent event = new QueryEvent(
                UUID.randomUUID().toString(), source, language,
                askedAbout, searchedFor, grounded, topScore, matchedNames,
                Instant.now(clock));

        // Serialization is separated from sending, and logged differently,
        // because the two failures mean different things. A broker being
        // unreachable is an operational condition that resolves itself; an
        // event that cannot be serialized is a bug in this code that would
        // otherwise make every analytics row vanish in silence. Catching both
        // at DEBUG would hide the second behind the first.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Resident query events cannot be serialized - the gap report will stay empty", e);
            return;
        }

        try {
            // Keyed by language so one language's traffic stays in order within
            // a partition; ordering across languages is meaningless here.
            template.send(QueryEvent.TOPIC, language.name(), payload);
        } catch (Exception e) {
            // Never rethrown. A resident asked a question and got an answer;
            // that the analytics copy did not make it is not their problem, and
            // must not become a 500 on the way out.
            log.debug("Could not send a resident query for analytics", e);
        }
    }
}
