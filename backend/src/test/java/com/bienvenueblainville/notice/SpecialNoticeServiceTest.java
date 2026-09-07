package com.bienvenueblainville.notice;

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
import static org.mockito.Mockito.when;

class SpecialNoticeServiceTest {
    @Mock
    private SpecialNoticeMapper mapper;

    private SpecialNoticeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SpecialNoticeService(mapper);
    }

    @Test
    void createReadsTheGeneratedIdBackFromTheSharedParamsMap() {
        doAnswer(invocation -> {
            Map<String, Object> params = invocation.getArgument(0);
            params.put("id", 7L);
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
}
