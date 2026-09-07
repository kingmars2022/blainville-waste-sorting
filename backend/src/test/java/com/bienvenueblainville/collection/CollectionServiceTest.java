package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
