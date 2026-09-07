package com.bienvenueblainville.collection;

import com.bienvenueblainville.collection.dto.CollectionEventRequest;
import com.bienvenueblainville.common.Sector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionServiceTest {
    @Mock
    private CollectionEventMapper mapper;

    private CollectionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CollectionService(mapper);
    }

    @Test
    void upcomingQueriesFromTodayThroughTheRequestedWindow() {
        when(mapper.findUpcoming(eq(Sector.north), any(), any())).thenReturn(List.of());

        service.upcoming(Sector.north, 14);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(mapper).findUpcoming(eq(Sector.north), startCaptor.capture(), endCaptor.capture());

        LocalDate today = LocalDate.now();
        assertThat(startCaptor.getValue()).isEqualTo(today);
        assertThat(endCaptor.getValue()).isEqualTo(today.plusDays(14));
    }

    @Test
    void createReadsTheGeneratedIdBackFromTheSharedParamsMap() {
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            // MySQL's JDBC driver hands back generated keys for BIGINT columns
            // as BigInteger, not Long — assert against that, not a Long literal.
            params.put("id", java.math.BigInteger.valueOf(3));
            return null;
        }).when(mapper).insert(any());

        CollectionEvent created = new CollectionEvent(
                3L, LocalDate.now(), Sector.all, CollectionType.organic, BinColor.brown,
                null, null, null, null);
        when(mapper.findById(3L)).thenReturn(Optional.of(created));

        CollectionEvent result = service.create(new CollectionEventRequest(
                LocalDate.now(), Sector.all, CollectionType.organic, BinColor.brown, null, null, null, null));

        assertThat(result.id()).isEqualTo(3L);
    }

    @Test
    void deleteRejectsAnUnknownId() {
        when(mapper.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");

        verify(mapper, never()).delete(99L);
    }
}
