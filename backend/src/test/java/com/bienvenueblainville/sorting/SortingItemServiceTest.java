package com.bienvenueblainville.sorting;

import com.bienvenueblainville.collection.BinColor;
import com.bienvenueblainville.common.LanguageCode;
import com.bienvenueblainville.sorting.dto.SortingItemRequest;
import com.bienvenueblainville.sorting.dto.SortingItemResponse;
import com.bienvenueblainville.sorting.dto.TranslationInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SortingItemServiceTest {
    @Mock
    private SortingItemMapper itemMapper;
    @Mock
    private SortingItemTranslationMapper translationMapper;
    @Mock
    private SortingItemKeywordMapper keywordMapper;

    private SortingItemService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SortingItemService(itemMapper, translationMapper, keywordMapper);
    }

    private SortingItemRequest sampleRequest() {
        return new SortingItemRequest(
                DestinationType.organic,
                BinColor.brown,
                "https://blainville.ca/example",
                new TranslationInput("Restes de fruits", "Bac brun", "Bordure de rue"),
                new TranslationInput("Fruit scraps", "Brown bin", "Curbside"),
                new TranslationInput("水果残渣", "棕桶", "路边"),
                List.of("fruit", " legume ", ""),
                List.of("fruit"),
                List.of("水果")
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void createReadsTheGeneratedIdBackFromTheSharedParamsMap() {
        // Simulates what MyBatis's Jdbc3KeyGenerator does: it mutates the same
        // Map instance passed as the insert parameter, adding the generated "id".
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            params.put("id", 42L);
            return null;
        }).when(itemMapper).insert(any());

        SortingItem created = new SortingItem(42L, DestinationType.organic, BinColor.brown, "https://blainville.ca/example");
        when(itemMapper.findById(42L)).thenReturn(Optional.of(created));
        when(translationMapper.findByItemId(42L)).thenReturn(List.of());
        when(keywordMapper.findByItemId(42L)).thenReturn(List.of());

        SortingItemResponse response = service.create(sampleRequest());

        assertThat(response.id()).isEqualTo(42L);
        verify(translationMapper).insert(eq(42L), eq(LanguageCode.fr), any(), any(), any());
        verify(translationMapper).insert(eq(42L), eq(LanguageCode.en), any(), any(), any());
        verify(translationMapper).insert(eq(42L), eq(LanguageCode.zh), any(), any(), any());
    }

    @Test
    void createTrimsBlankKeywordsBeforeInsertingAndSkipsEmptyLanguages() {
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            params.put("id", 1L);
            return null;
        }).when(itemMapper).insert(any());
        when(itemMapper.findById(1L)).thenReturn(Optional.of(
                new SortingItem(1L, DestinationType.organic, BinColor.brown, null)));
        when(translationMapper.findByItemId(1L)).thenReturn(List.of());
        when(keywordMapper.findByItemId(1L)).thenReturn(List.of());

        service.create(sampleRequest());

        verify(keywordMapper).insertBatch(eq(1L), eq(LanguageCode.fr), eq(List.of("fruit", "legume")));
        verify(keywordMapper).insertBatch(eq(1L), eq(LanguageCode.en), eq(List.of("fruit")));
        verify(keywordMapper).insertBatch(eq(1L), eq(LanguageCode.zh), eq(List.of("水果")));
    }

    @Test
    void deleteRemovesChildRowsBeforeTheParentToRespectForeignKeys() {
        when(itemMapper.findById(5L)).thenReturn(Optional.of(
                new SortingItem(5L, DestinationType.organic, BinColor.brown, null)));

        service.delete(5L);

        var inOrder = org.mockito.Mockito.inOrder(keywordMapper, translationMapper, itemMapper);
        inOrder.verify(keywordMapper).deleteByItemId(5L);
        inOrder.verify(translationMapper).deleteByItemId(5L);
        inOrder.verify(itemMapper).delete(5L);
    }

    @Test
    void deleteRejectsAnUnknownId() {
        when(itemMapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");

        verify(itemMapper, never()).delete(anyLong());
    }
}
