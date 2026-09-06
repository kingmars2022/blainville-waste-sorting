package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

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
}

