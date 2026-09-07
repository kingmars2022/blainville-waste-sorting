package com.bienvenueblainville.sorting;

import com.bienvenueblainville.sorting.dto.SortingItemRequest;
import com.bienvenueblainville.sorting.dto.SortingItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sorting-items")
public class AdminSortingItemController {
    private final SortingItemService service;

    public AdminSortingItemController(SortingItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<SortingItemResponse> all() {
        return service.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SortingItemResponse create(@Valid @RequestBody SortingItemRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SortingItemResponse update(@PathVariable Long id, @Valid @RequestBody SortingItemRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
