package com.bienvenueblainville.collection;

import com.bienvenueblainville.collection.dto.CollectionEventRequest;
import com.bienvenueblainville.common.Sector;
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

    public List<CollectionEvent> upcoming(Sector sector, int days) {
        LocalDate today = LocalDate.now(clock);
        return mapper.findUpcoming(sector, today, today.plusDays(days));
    }

    public List<CollectionEvent> all() {
        return mapper.findAll();
    }

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

    public void delete(Long id) {
        requireExists(id);
        mapper.delete(id);
    }

    private void requireExists(Long id) {
        mapper.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection event " + id + " not found"));
    }
}

