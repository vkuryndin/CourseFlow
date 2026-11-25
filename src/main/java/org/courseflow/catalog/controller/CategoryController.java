package org.courseflow.catalog.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.courseflow.catalog.dto.CategoryCreateRequest;
import org.courseflow.catalog.dto.CategoryResponse;
import org.courseflow.catalog.service.CategoryService;
import org.courseflow.common.api.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

  private final CategoryService categoryService;

  public CategoryController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody CategoryCreateRequest request) {
    CategoryResponse response = categoryService.create(request);

    URI location = URI.create("/api/categories/" + response.id());

    return ResponseEntity.created(location).body(response);
  }

  @PutMapping("/{id}")
  public CategoryResponse update(
      @PathVariable Long id, @Valid @RequestBody CategoryCreateRequest request) {
    return categoryService.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    categoryService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}")
  public CategoryResponse getById(@PathVariable Long id) {
    return categoryService.getById(id);
  }

  @GetMapping
  public PageResponse<CategoryResponse> findAll(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false)
          @io.swagger.v3.oas.annotations.Parameter(
              deprecated = true,
              description = "Legacy alias for q")
          String search,
      @RequestParam(defaultValue = "name") String sortBy,
      @RequestParam(defaultValue = "asc") String direction) {
    return categoryService.findAll(page, size, q != null ? q : search, sortBy, direction);
  }
}
