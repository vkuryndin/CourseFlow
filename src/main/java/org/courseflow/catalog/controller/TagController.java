
package org.courseflow.catalog.controller;

import jakarta.validation.Valid;

import org.courseflow.catalog.dto.TagRequest;
import org.courseflow.catalog.dto.TagResponse;
import org.courseflow.catalog.service.TagService;
import org.courseflow.common.api.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public PageResponse<TagResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return tagService.findAll(
                page,
                size,
                q,
                sortBy,
                direction
        );
    }

    @GetMapping("/{id}")
    public TagResponse getById(@PathVariable Long id) {
        return tagService.getById(id);
    }

    @PostMapping
    public ResponseEntity<TagResponse> create(
            @Valid @RequestBody TagRequest request
    ) {
        TagResponse response = tagService.create(request);

        return ResponseEntity
                .created(
                        URI.create("/api/tags/" + response.id())
                )
                .body(response);
    }

    @PutMapping("/{id}")
    public TagResponse update(
            @PathVariable Long id,
            @Valid @RequestBody TagRequest request
    ) {
        return tagService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ) {
        tagService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
