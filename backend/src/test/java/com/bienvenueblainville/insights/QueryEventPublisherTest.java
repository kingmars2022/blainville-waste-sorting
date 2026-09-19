package com.bienvenueblainville.insights;

import com.bienvenueblainville.common.LanguageCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * This publisher's contract is mostly about what it must <em>not</em> do:
 * never throw, and never touch a broker the deployment does not have.
 */
class QueryEventPublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);

    @SuppressWarnings("unchecked")
    private QueryEventPublisher publisher(boolean enabled) {
        ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        // JavaTimeModule, as Spring Boot's auto-configured mapper has: the
        // event carries an Instant, and a bare ObjectMapper cannot write one.
        // Writing this test with a bare mapper is how the silent-serialization
        // failure below was found in the first place.
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        return new QueryEventPublisher(provider, mapper, enabled);
    }

    @Test
    void publishesWhenEventsAreOn() {
        publisher(true).record("text", LanguageCode.fr, "aquarium", "aquarium", false, 0d, List.of());

        verify(template).send(any(), any(), any());
    }

    @Test
    void doesNotTouchTheProducerWhenEventsAreOff() {
        // The regression this pins: without the gate, a deployment with no
        // Kafka had the producer retrying a dead broker once per resident
        // question. Kafka's send() also blocks for max.block.ms while broker
        // metadata is unavailable, so that was a request-path stall, not just
        // noise in the log.
        publisher(false).record("text", LanguageCode.fr, "aquarium", "aquarium", false, 0d, List.of());

        verify(template, never()).send(any(), any(), any());
    }

    @Test
    void aSerializationFailureIsLoudRatherThanSilent() {
        // Different failure, different treatment. An unreachable broker is an
        // operational condition; an event that cannot be serialized is a bug
        // that would otherwise empty the gap report without anyone noticing.
        // Either way the resident's question still succeeds.
        ObjectProvider<KafkaTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(template);
        QueryEventPublisher broken = new QueryEventPublisher(provider, new ObjectMapper(), true);

        assertThatNoException().isThrownBy(() ->
                broken.record("text", LanguageCode.en, "aquarium", "aquarium", false, 0d, List.of()));
        verify(template, never()).send(any(), any(), any());
    }

    @Test
    void swallowsAProducerFailureRatherThanFailingTheQuestion() {
        // A resident asked something and got an answer. That the analytics copy
        // did not make it is not their problem, and must not become a 500 on
        // the way out.
        when(template.send(any(), any(), any())).thenThrow(new IllegalStateException("broker down"));

        assertThatNoException().isThrownBy(() ->
                publisher(true).record("text", LanguageCode.en, "aquarium", "aquarium", false, 0d, List.of()));
    }
}
