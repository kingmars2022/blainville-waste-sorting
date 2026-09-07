package com.bienvenueblainville.notice;

import com.bienvenueblainville.notice.dto.NoticeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class SpecialNoticeService {
    private final SpecialNoticeMapper mapper;
    private final Clock clock;

    public SpecialNoticeService(SpecialNoticeMapper mapper) {
        this.mapper = mapper;
        this.clock = Clock.systemDefaultZone();
    }

    public List<SpecialNotice> active() {
        return mapper.findActive(LocalDate.now(clock));
    }

    public List<SpecialNotice> all() {
        return mapper.findAll();
    }

    public SpecialNotice create(NoticeRequest request) {
        SpecialNotice notice = toNotice(null, request);
        mapper.insert(notice);
        return mapper.findById(notice.id()).orElseThrow();
    }

    public SpecialNotice update(Long id, NoticeRequest request) {
        requireExists(id);
        mapper.update(toNotice(id, request));
        return mapper.findById(id).orElseThrow();
    }

    public void delete(Long id) {
        requireExists(id);
        mapper.delete(id);
    }

    private void requireExists(Long id) {
        mapper.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Notice " + id + " not found"));
    }

    private SpecialNotice toNotice(Long id, NoticeRequest request) {
        return new SpecialNotice(
                id,
                request.startsOn(),
                request.endsOn(),
                request.titleFr(),
                request.titleEn(),
                request.titleZh(),
                request.bodyFr(),
                request.bodyEn(),
                request.bodyZh(),
                request.sourceUrl(),
                request.active()
        );
    }
}
