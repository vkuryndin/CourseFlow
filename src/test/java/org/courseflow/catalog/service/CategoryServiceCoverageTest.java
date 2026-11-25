package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.category;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.repository.CategoryRepository;
import org.courseflow.catalog.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class CategoryServiceCoverageTest {
  private final CategoryRepository repository = mock(CategoryRepository.class);
  private final CategoryService service =
      new CategoryService(repository, mock(CourseRepository.class));

  @Test
  void getExistingCategory() {
    when(repository.findById(30L)).thenReturn(Optional.of(category()));
    assertEquals("Java", service.getById(30L).name());
  }

  @Test
  void blankSearchListsAll() {
    var page = PageRequest.of(0, 10, Sort.by("name", "id"));
    when(repository.findAll(page)).thenReturn(new PageImpl<>(List.of(category()), page, 1));
    assertEquals(30L, service.findAll(0, 10, "  ", "name", "asc").content().get(0).id());
  }

  @Test
  void searchTrimsInput() {
    var page = PageRequest.of(0, 10, Sort.by("id"));
    when(repository.findByNameContainingIgnoreCase("JaVa", page))
        .thenReturn(new PageImpl<>(List.of(category()), page, 1));
    assertEquals(1, service.findAll(0, 10, " JaVa ", "id", "asc").totalElements());
  }

  @Test
  void nullSearchListsAll() {
    var page = PageRequest.of(0, 10, Sort.by("id"));
    when(repository.findAll(page)).thenReturn(new PageImpl<>(List.of()));
    assertTrue(service.findAll(0, 10, null, "id", "asc").content().isEmpty());
  }
}
