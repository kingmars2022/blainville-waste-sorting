package com.bienvenueblainville.notice;

import com.bienvenueblainville.events.OutboxRecorder;
import com.bienvenueblainville.notice.dto.NoticeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpecialNoticeServiceTest {
    @Mock
    private SpecialNoticeMapper mapper;
    @Mock
    private OutboxRecorder outbox;

    private SpecialNoticeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SpecialNoticeService(mapper, outbox);
    }

    @Test
    void createReadsTheGeneratedIdBackFromTheSharedParamsMap() {
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            // MySQL's JDBC driver hands back generated keys for BIGINT columns
            // as BigInteger, not Long — assert against that, not a Long literal.
            params.put("id", java.math.BigInteger.valueOf(7));
            return null;
        }).when(mapper).insert(any());

        SpecialNotice created = new SpecialNotice(
                7L, LocalDate.now(), LocalDate.now().plusDays(1),
                "fr", "en", "zh", "corps fr", "corps en", "corps zh", null, true);
        when(mapper.findById(7L)).thenReturn(Optional.of(created));

        SpecialNotice result = service.create(new NoticeRequest(
                LocalDate.now(), LocalDate.now().plusDays(1),
                "fr", "en", "zh", "corps fr", "corps en", "corps zh", null, true));

        assertThat(result.id()).isEqualTo(7L);
    }

    @Test
    void recordsAnOutboxEventCarryingTheStoredNoticeRatherThanTheRequest() {
        // The audit trail has to describe what is in the database, not what was
        // asked for - those differ whenever a default or a trigger fills a field.
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            params.put("id", java.math.BigInteger.valueOf(7));
            return null;
        }).when(mapper).insert(any());

        SpecialNotice stored = new SpecialNotice(
                7L, LocalDate.now(), LocalDate.now().plusDays(1),
                "fr", "en", "zh", "corps fr", "corps en", "corps zh", null, true);
        when(mapper.findById(7L)).thenReturn(Optional.of(stored));

        service.create(new NoticeRequest(
                LocalDate.now(), LocalDate.now().plusDays(1),
                "fr", "en", "zh", "corps fr", "corps en", "corps zh", null, true));

        verify(outbox).recordNoticeEvent(eq("created"), eq(7L), any(), isNull(), eq(stored));
    }

    @Test
    void recordsBothSidesOfADeletionBeforeTheRowIsGone() {
        SpecialNotice existing = new SpecialNotice(
                9L, LocalDate.now(), LocalDate.now().plusDays(1),
                "fr", "en", "zh", "corps fr", "corps en", "corps zh", null, true);
        when(mapper.findById(9L)).thenReturn(Optional.of(existing));

        service.delete(9L);

        // After the delete there is nothing left to describe what was removed,
        // so the snapshot has to be taken first.
        verify(outbox).recordNoticeEvent(eq("deleted"), eq(9L), any(), eq(existing), isNull());
    }
}
