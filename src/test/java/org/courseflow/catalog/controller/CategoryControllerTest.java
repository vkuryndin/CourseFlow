package org.courseflow.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.catalog.dto.CategoryCreateRequest;
import org.courseflow.catalog.dto.CategoryResponse;
import org.courseflow.catalog.service.CategoryService;
import org.courseflow.common.api.PageResponse;
import org.junit.jupiter.api.Test;

/** Direct controller contract tests; no Spring MVC runtime. */
class CategoryControllerTest {
  private final CategoryService categoryService = mock(CategoryService.class);
  private final CategoryController controller = new CategoryController(categoryService);

  @Test
  void createDelegatesAndReturnsContract() {
    CategoryCreateRequest request = mock(CategoryCreateRequest.class);
    CategoryResponse expected = mock(CategoryResponse.class);
    when(expected.id()).thenReturn(999L);
    when(categoryService.create(request)).thenReturn(expected);

    var actual = controller.create(request);

    assertEquals(201, actual.getStatusCode().value());
    assertSame(expected, actual.getBody());
    assertEquals("/api/categories/999", actual.getHeaders().getLocation().toString());
    verify(categoryService).create(request);
  }

  @Test
  void updateDelegatesAndReturnsContract() {
    Long id = 101L;
    CategoryCreateRequest request = mock(CategoryCreateRequest.class);
    CategoryResponse expected = mock(CategoryResponse.class);
    when(categoryService.update(id, request)).thenReturn(expected);

    var actual = controller.update(id, request);

    assertSame(expected, actual);
    verify(categoryService).update(id, request);
  }

  @Test
  void deleteDelegatesAndReturnsContract() {
    Long id = 101L;

    var actual = controller.delete(id);

    assertEquals(204, actual.getStatusCode().value());
    assertNull(actual.getBody());
    verify(categoryService).delete(id);
  }

  @Test
  void getByIdDelegatesAndReturnsContract() {
    Long id = 101L;
    CategoryResponse expected = mock(CategoryResponse.class);
    when(categoryService.getById(id)).thenReturn(expected);

    var actual = controller.getById(id);

    assertSame(expected, actual);
    verify(categoryService).getById(id);
  }

  @Test
  void findAllDelegatesAndReturnsContract() {
    int page = 1;
    int size = 15;
    String q = "search";
    String search = "legacy";
    String sortBy = "id";
    String direction = "desc";
    PageResponse<CategoryResponse> expected =
        new PageResponse<>(List.of(), 1, 15, 0, 0, false, true);
    when(categoryService.findAll(page, size, q, sortBy, direction)).thenReturn(expected);

    var actual = controller.findAll(page, size, q, search, sortBy, direction);

    assertSame(expected, actual);
    verify(categoryService).findAll(page, size, q, sortBy, direction);
  }

  @Test
  void legacySearchAliasIsUsedWhenQIsAbsent() {
    PageResponse<CategoryResponse> expected =
        new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
    when(categoryService.findAll(0, 20, "legacy", "name", "asc")).thenReturn(expected);

    assertSame(expected, controller.findAll(0, 20, null, "legacy", "name", "asc"));
  }
}
