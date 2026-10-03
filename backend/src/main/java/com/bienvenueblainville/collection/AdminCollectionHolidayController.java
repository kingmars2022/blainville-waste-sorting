package com.bienvenueblainville.collection;

import com.bienvenueblainville.collection.dto.CollectionHolidayRequest;
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

/**
 * Closed days, for administrators.
 *
 * <p>Not paginated, unlike the schedule: a municipal year has a handful of
 * these, and the list is meant to be read whole so somebody can see at a
 * glance what the calendar already knows about.
 */
@RestController
@RequestMapping("/api/admin/collection-holidays")
public class AdminCollectionHolidayController {
    private final CollectionHolidayService service;

    public AdminCollectionHolidayController(CollectionHolidayService service) {
        this.service = service;
    }

    @GetMapping
    public List<CollectionHoliday> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionHoliday create(@Valid @RequestBody CollectionHolidayRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CollectionHoliday update(@PathVariable Long id, @Valid @RequestBody CollectionHolidayRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
