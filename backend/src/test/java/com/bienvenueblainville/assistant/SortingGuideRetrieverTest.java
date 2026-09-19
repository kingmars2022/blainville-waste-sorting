package com.bienvenueblainville.assistant;

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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SortingGuideRetrieverTest {
    @Mock
    private SortingSearchMapper mapper;

    private SortingGuideRetriever retriever;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        retriever = new SortingGuideRetriever(mapper);
    }

    @Test
    void dropsResultsFarBelowTheBestMatch() {
        when(mapper.search(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of(
                item("Papiers et cartons souilles", 10.0),
                item("Contenants et emballages", 5.0),   // 0.5 of best - kept
                item("Sapins de Noel", 1.0)              // 0.1 of best - dropped
        ));

        List<RetrievedItem> kept = retriever.retrieve("boîte à pizza", LanguageCode.fr);

        assertThat(kept).extracting(RetrievedItem::name)
                .containsExactly("Papiers et cartons souilles", "Contenants et emballages");
    }

    @Test
    void searchesChineseThroughTheNgramIndexAndTheOthersThroughTheWordIndex() {
        // Not a style choice: the ngram parser is the only one that can tokenize
        // Chinese, and the only one loose enough to make a French refusal
        // impossible. Getting this backwards silently breaks one or the other.
        when(mapper.search(any(), any(), anyInt(), anyBoolean())).thenReturn(List.of());

        retriever.retrieve("废电池", LanguageCode.zh);
        verify(mapper).search(eq(LanguageCode.zh), any(), anyInt(), eq(true));

        retriever.retrieve("pizza", LanguageCode.fr);
        verify(mapper).search(eq(LanguageCode.fr), any(), anyInt(), eq(false));
    }

    @Test
    void doesNotSearchAtAllWhenTheQuestionIsOnlyFunctionWords() {
        assertThat(retriever.retrieve("Quel est le plus ?", LanguageCode.fr)).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void doesNotSearchOnBlankInput() {
        assertThat(retriever.retrieve("   ", LanguageCode.en)).isEmpty();
        verify(mapper, never()).search(any(), any(), anyInt(), anyBoolean());
    }

    private static RetrievedItem item(String name, double score) {
        return new RetrievedItem(1L, LanguageCode.fr, name, "instruction", null,
                DestinationType.organic, BinColor.brown, null, score);
    }
}
