package com.bienvenueblainville.collection;

import com.bienvenueblainville.collection.dto.CollectionEventRequest;
import com.bienvenueblainville.common.Page;
import com.bienvenueblainville.common.Sector;
import com.bienvenueblainville.config.CacheConfig;
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
public class CollectionService {
    private final CollectionEventMapper mapper;
    private final Clock clock;

    public CollectionService(CollectionEventMapper mapper) {
        this.mapper = mapper;
        this.clock = Clock.systemDefaultZone();
    }

    /**
     * The hottest read in the app: every home page load calls it.
     *
     * <p>The cache key has to include today's date. The result depends on
     * {@code LocalDate.now(clock)}, so a key of just (sector, days) would keep
     * serving yesterday's "next collection" after midnight — the one moment
     * this page absolutely has to be right. {@code days} is validated to 1..62
     * by the controller, and {@code sector} is an enum, so the key space stays
     * small and bounded rather than being driven by arbitrary user input.
     */
    @Cacheable(
            cacheNames = CacheConfig.UPCOMING_COLLECTIONS,
            key = "#root.target.today().toString() + ':' + #sector + ':' + #days"
    )
    public List<CollectionEvent> upcoming(Sector sector, int days) {
        LocalDate today = today();
        return mapper.findUpcoming(sector, today, today.plusDays(days));
    }

    /** Exposed so the cache key above can depend on the same clock this service reads. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<CollectionEvent> all() {
        return mapper.findAll();
    }

    /**
     * One page of the schedule, newest first.
     *
     * <p>The admin console used to list every row. That was fine when the
     * calendar was a short hand-written seed; since V12 it extends itself to a
     * rolling horizon and never prunes what is behind, so the table only grows
     * - and an administrator looking for next Thursday had to scroll past
     * every collection since August.
     */
    public Page<CollectionEvent> page(int page, int size) {
        long total = mapper.countAll();
        return new Page<>(mapper.findPage(page * size, size), page, size, total);
    }

    // allEntries: a single schedule edit can change the answer for several
    // sectors and every horizon at once, so there is no useful narrower key
    // to evict. The cache refills on the next request.
    @CacheEvict(cacheNames = CacheConfig.UPCOMING_COLLECTIONS, allEntries = true)
    public CollectionEvent create(CollectionEventRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("collectionDate", request.collectionDate());
        params.put("sector", request.sector());
        params.put("collectionType", request.collectionType());
        params.put("binColor", request.binColor());
        params.put("noteFr", request.noteFr());
        params.put("noteEn", request.noteEn());
        params.put("noteZh", request.noteZh());
        params.put("sourceUrl", request.sourceUrl());

        mapper.insert(params);
        Long generatedId = ((Number) params.get("id")).longValue();
        return mapper.findById(generatedId).orElseThrow();
    }

    @CacheEvict(cacheNames = CacheConfig.UPCOMING_COLLECTIONS, allEntries = true)
    public CollectionEvent update(Long id, CollectionEventRequest request) {
        requireExists(id);
        mapper.update(
                id,
                request.collectionDate(),
                request.sector(),
                request.collectionType(),
                request.binColor(),
                request.noteFr(),
                request.noteEn(),
                request.noteZh(),
                request.sourceUrl()
        );
        return mapper.findById(id).orElseThrow();
    }

    @CacheEvict(cacheNames = CacheConfig.UPCOMING_COLLECTIONS, allEntries = true)
    public void delete(Long id) {
        requireExists(id);
        mapper.delete(id);
    }

    private void requireExists(Long id) {
        mapper.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection event " + id + " not found"));
    }
}

