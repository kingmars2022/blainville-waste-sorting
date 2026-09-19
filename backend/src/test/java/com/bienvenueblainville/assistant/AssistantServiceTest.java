package com.bienvenueblainville.assistant;

import com.bienvenueblainville.assistant.dto.AssistantAnswer;
import com.bienvenueblainville.assistant.dto.AssistantRequest;
import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.insights.QueryEventPublisher;
import com.bienvenueblainville.sorting.DestinationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {
    @Mock
    private SortingGuideRetriever retriever;
    @Mock
    private AnswerComposer composer;
    @Mock
    private QueryEventPublisher queries;
    @Mock
    private AnswerCache cache;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cache.find(any(), any(), any())).thenReturn(Optional.empty());
        service = new AssistantService(retriever, composer, queries, cache);
    }

    @Test
    void refusesWithoutCallingTheComposerWhenNothingWasRetrieved() {
        // The safety property of the whole feature. If retrieval finds nothing,
        // there is no grounding, so there must be no generated answer - not a
        // hedged one, not a "probably the black bin", none. Asserting the
        // composer is never even reached is stronger than asserting on wording,
        // because it holds no matter which composer is configured.
        when(retriever.retrieve(any(), any())).thenReturn(List.of());

        AssistantAnswer answer = service.ask(
                new AssistantRequest("Comment réparer ma voiture ?", LanguageCode.fr));

        verify(composer, never()).compose(any(), any());
        verify(composer, never()).composeWithMetadata(any(), any());
        assertThat(answer.provider()).isEqualTo("template");
        assertThat(answer.grounded()).isFalse();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).contains("blainville.ca");

        // Cached, because the same unlisted material gets asked about
        // repeatedly and every repeat otherwise re-runs a full-text search to
        // find nothing again.
        verify(cache).store(any(), eq(LanguageCode.fr), any(), eq(answer));

        // The refusal is the row the city needs: a question the guide could
        // not answer used to vanish without trace.
        verify(queries).record(eq("text"), eq(LanguageCode.fr), any(), any(),
                eq(false), eq(0d), eq(List.of()));
    }

    @Test
    void refusesInTheLanguageTheResidentAsked() {
        when(retriever.retrieve(any(), any())).thenReturn(List.of());

        assertThat(service.ask(new AssistantRequest("what is the capital of Mongolia", LanguageCode.en))
                .answer()).contains("can't find");
        assertThat(service.ask(new AssistantRequest("今天天气怎么样？", LanguageCode.zh))
                .answer()).contains("找不到");
    }

    @Test
    void aRepeatQuestionIsServedFromCacheButStillCounted() {
        // The trap this avoids: caching the whole method would stop repeats
        // being recorded, and the gap report would then under-count precisely
        // the questions residents ask most.
        AssistantAnswer previous = new AssistantAnswer("Bac brun.", true, "template", List.of());
        when(cache.find(any(), any(), any())).thenReturn(Optional.of(previous));

        AssistantAnswer answer = service.ask(new AssistantRequest("boîte à pizza", LanguageCode.fr));

        assertThat(answer).isSameAs(previous);
        // Retrieval is skipped - that and the model call are the expensive
        // parts. Normalizing the question for the analytics row still happens,
        // because the gap report groups on it.
        verify(retriever, never()).retrieve(any(), any());
        verify(composer, never()).composeWithMetadata(any(), any());
        verify(queries).record(eq("text-cached"), eq(LanguageCode.fr), any(), any(),
                eq(true), eq(0d), eq(List.of()));
    }

    @Test
    void reportsEveryEntryTheAnswerWasAllowedToUse() {
        when(retriever.retrieve(any(), any())).thenReturn(List.of(
                new RetrievedItem(7L, LanguageCode.fr, "Papiers et cartons souilles",
                        "Dans le bac brun.", null, DestinationType.organic, BinColor.brown,
                        "https://blainville.ca/tri", 8.8954321)));
        when(composer.composeWithMetadata(any(), any()))
                .thenReturn(new AnswerComposer.Composition("Bac brun.", "template"));

        AssistantAnswer answer = service.ask(
                new AssistantRequest("boîte à pizza", LanguageCode.fr));

        assertThat(answer.grounded()).isTrue();
        assertThat(answer.provider()).isEqualTo("template");
        assertThat(answer.answer()).isEqualTo("Bac brun.");
        assertThat(answer.sources()).singleElement().satisfies(source -> {
            assertThat(source.itemId()).isEqualTo(7L);
            assertThat(source.destinationType()).isEqualTo("organic");
            assertThat(source.binColor()).isEqualTo("brown");
            assertThat(source.sourceUrl()).isEqualTo("https://blainville.ca/tri");
            // Rounded, because a raw MySQL relevance float in a public API is
            // noise the caller has to look at on every response.
            assertThat(source.score()).isEqualTo(8.895);
        });
    }
}
