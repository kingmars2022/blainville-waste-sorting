package com.bienvenueblainville.sorting;

import com.bienvenueblainville.assistant.AnswerCache;
import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.common.LanguageCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cost of listing the guide, held to a constant.
 *
 * <p>The admin listing used to map every row through the single-item path,
 * which costs three lookups of its own: fourteen entries meant forty-three
 * queries, and the number grew with the guide. Counting mapper calls is the
 * only way to assert that from a test — the result is identical either way,
 * which is exactly why it went unnoticed.
 */
class SortingItemListingTest {
    @Mock
    private SortingItemMapper itemMapper;
    @Mock
    private SortingItemTranslationMapper translationMapper;
    @Mock
    private SortingItemKeywordMapper keywordMapper;
    @Mock
    private SortingItemExampleMapper exampleMapper;
    @Mock
    private AnswerCache answerCache;

    private SortingItemService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SortingItemService(itemMapper, translationMapper, keywordMapper, exampleMapper, answerCache);
    }

    private void guideOf(int itemCount) {
        List<SortingItem> items = new ArrayList<>();
        List<SortingItemTranslation> translations = new ArrayList<>();
        for (long id = 1; id <= itemCount; id++) {
            items.add(new SortingItem(id, DestinationType.organic, BinColor.brown, null));
            translations.add(new SortingItemTranslation(
                    id, id, LanguageCode.fr, "Nom " + id, "Instruction", null, null));
        }
        when(itemMapper.findAll()).thenReturn(items);
        when(translationMapper.findByItemIds(any())).thenReturn(translations);
        when(keywordMapper.findByItemIds(any())).thenReturn(List.of());
        when(exampleMapper.findByItemIds(any())).thenReturn(List.of());
    }

    @Test
    void listingCostsTheSameFourQueriesWhateverTheGuideHolds() {
        guideOf(50);

        List<?> listed = service.all();

        assertThat(listed).hasSize(50);
        verify(itemMapper).findAll();
        verify(translationMapper).findByItemIds(any());
        verify(keywordMapper).findByItemIds(any());
        verify(exampleMapper).findByItemIds(any());
        // The regression: one lookup per item, per child table.
        verify(translationMapper, never()).findByItemId(anyLong());
        verify(keywordMapper, never()).findByItemId(anyLong());
        verify(exampleMapper, never()).findByItemId(anyLong());
    }

    @Test
    void asksForEveryItemIdExactlyOnce() {
        guideOf(3);

        service.all();

        verify(translationMapper, times(1)).findByItemIds(List.of(1L, 2L, 3L));
    }

    @Test
    void doesNotQueryTheChildTablesAtAllForAnEmptyGuide() {
        when(itemMapper.findAll()).thenReturn(List.of());

        assertThat(service.all()).isEmpty();

        // `where item_id in ()` is not valid SQL, so the empty case has to
        // short-circuit rather than build a query with no values in it.
        verify(translationMapper, never()).findByItemIds(any());
        verify(keywordMapper, never()).findByItemIds(any());
        verify(exampleMapper, never()).findByItemIds(any());
    }
}
