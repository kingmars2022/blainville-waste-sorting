package com.bienvenueblainville.collection;

import com.bienvenueblainville.collection.dto.CollectionEventRequest;
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
@RequestMapping("/api/admin/collections")
public class AdminCollectionController {
    private final CollectionService service;

    public AdminCollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CollectionEvent> all() {
        return service.all();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CollectionEvent create(@Valid @RequestBody CollectionEventRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public CollectionEvent update(@PathVariable Long id, @Valid @RequestBody CollectionEventRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
