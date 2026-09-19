package com.bienvenueblainville.sorting;

import com.bienvenueblainville.sorting.dto.SortingItemResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The sorting guide, for residents.
 *
 * <p>This endpoint is why the frontend no longer ships its own copy of the
 * guide. Before it existed, the cards residents read came from a TypeScript
 * file while the assistant and the admin console read MySQL — so an
 * administrator could correct an entry and the page nobody else looks at would
 * change, while the page everybody looks at would not.
 */
@RestController
@RequestMapping("/api/sorting-items")
public class SortingItemController {
    private final SortingItemService service;

    public SortingItemController(SortingItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<SortingItemResponse> guide() {
        return service.guide();
    }
}
