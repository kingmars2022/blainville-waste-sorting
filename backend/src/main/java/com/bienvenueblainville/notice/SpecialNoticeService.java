package com.bienvenueblainville.notice;

import com.bienvenueblainville.notice.dto.NoticeRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> params = new HashMap<>();
        params.put("startsOn", request.startsOn());
        params.put("endsOn", request.endsOn());
        params.put("titleFr", request.titleFr());
        params.put("titleEn", request.titleEn());
        params.put("titleZh", request.titleZh());
        params.put("bodyFr", request.bodyFr());
        params.put("bodyEn", request.bodyEn());
        params.put("bodyZh", request.bodyZh());
        params.put("sourceUrl", request.sourceUrl());
        params.put("active", request.active());

        mapper.insert(params);
        Long generatedId = (Long) params.get("id");
        return mapper.findById(generatedId).orElseThrow();
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
