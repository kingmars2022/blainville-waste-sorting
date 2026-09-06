package com.bienvenueblainville.collection;

import com.bienvenueblainville.common.Sector;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping("/upcoming")
    public List<CollectionEvent> upcoming(
            @RequestParam Sector sector,
            @RequestParam(defaultValue = "14") @Min(1) @Max(62) int days
    ) {
        return service.upcoming(sector, days);
    }
}

