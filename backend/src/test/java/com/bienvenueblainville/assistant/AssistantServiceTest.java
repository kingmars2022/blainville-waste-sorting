package com.bienvenueblainville.assistant;

import com.bienvenueblainville.assistant.dto.AssistantAnswer;
import com.bienvenueblainville.assistant.dto.AssistantRequest;
import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.sorting.DestinationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {
    @Mock
    private SortingGuideRetriever retriever;
    @Mock
    private AnswerComposer composer;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(composer.providerName()).thenReturn("template");
        service = new AssistantService(retriever, composer);
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
        assertThat(answer.grounded()).isFalse();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.answer()).contains("blainville.ca");
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
    void reportsEveryEntryTheAnswerWasAllowedToUse() {
        when(retriever.retrieve(any(), any())).thenReturn(List.of(
                new RetrievedItem(7L, LanguageCode.fr, "Papiers et cartons souilles",
                        "Dans le bac brun.", null, DestinationType.organic, BinColor.brown,
                        "https://blainville.ca/tri", 8.8954321)));
        when(composer.compose(any(), any())).thenReturn("Bac brun.");

        AssistantAnswer answer = service.ask(
                new AssistantRequest("boîte à pizza", LanguageCode.fr));

        assertThat(answer.grounded()).isTrue();
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
