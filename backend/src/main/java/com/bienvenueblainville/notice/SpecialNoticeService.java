package com.bienvenueblainville.notice;

import com.bienvenueblainville.config.CacheConfig;
import com.bienvenueblainville.notice.dto.NoticeRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * Read on every page load alongside the collection schedule. Cached by
     * day for the same reason {@code CollectionService.upcoming} is: the
     * query is "what is visible today", so the day has to be part of the key
     * or a notice would stay visible past its {@code endsOn} date.
     */
    @Cacheable(cacheNames = CacheConfig.ACTIVE_NOTICES, key = "#root.target.today().toString()")
    public List<SpecialNotice> active() {
        return mapper.findActive(today());
    }

    /** Exposed so the cache key above can depend on the same clock this service reads. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<SpecialNotice> all() {
        return mapper.findAll();
    }

    // A notice publish is the one write residents notice immediately (a
    // cancelled collection, a storm delay), so it evicts rather than waiting
    // out the TTL.
    @CacheEvict(cacheNames = CacheConfig.ACTIVE_NOTICES, allEntries = true)
    public SpecialNotice create(NoticeRequest request) {
        requireValidDateRange(request);

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
        Long generatedId = ((Number) params.get("id")).longValue();
        return mapper.findById(generatedId).orElseThrow();
    }

    @CacheEvict(cacheNames = CacheConfig.ACTIVE_NOTICES, allEntries = true)
    public SpecialNotice update(Long id, NoticeRequest request) {
        requireExists(id);
        requireValidDateRange(request);
        mapper.update(toNotice(id, request));
        return mapper.findById(id).orElseThrow();
    }

    @CacheEvict(cacheNames = CacheConfig.ACTIVE_NOTICES, allEntries = true)
    public void delete(Long id) {
        requireExists(id);
        mapper.delete(id);
    }

    private void requireValidDateRange(NoticeRequest request) {
        if (request.endsOn().isBefore(request.startsOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endsOn must not be before startsOn.");
        }
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
