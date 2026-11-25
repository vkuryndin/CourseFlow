package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.dto.TagRequest;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.catalog.repository.TagRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class TagServiceTest {
  private final TagRepository repo = mock(TagRepository.class);
  private final TagService service = new TagService(repo);

  @Test
  void createTrimsName() {
    when(repo.saveAndFlush(any(Tag.class))).thenAnswer((i) -> id(i.getArgument(0), 1));
    var r = service.create(new TagRequest(null, " Java "));

    assertEquals("Java", r.name());
    assertEquals(1L, r.id());
  }

  @Test
  void blankAndDuplicateNamesRejected() {
    assertThrows(BadRequestException.class, () -> service.create(new TagRequest(null, " ")));
    when(repo.existsByNameIgnoreCase("Java")).thenReturn(true);
    assertThrows(
        BusinessConflictException.class, () -> service.create(new TagRequest(null, "Java")));
    verify(repo, never()).saveAndFlush(any(Tag.class));
  }

  @Test
  void concurrentDuplicateTranslated() {
    when(repo.saveAndFlush(any(Tag.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate"));

    assertThrows(
        BusinessConflictException.class, () -> service.create(new TagRequest(null, "Java")));
  }

  @Test
  void updateChecksVersionAndName() {
    Tag t = id(new Tag("Old"), 1);
    when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(t));

    assertThrows(
        OptimisticLockingFailureException.class,
        () -> service.update(1L, new TagRequest(1L, "New")));
    assertEquals("Old", t.getName());
    assertEquals("New", service.update(1L, new TagRequest(0L, " New ")).name());
    verify(repo).flush();
  }

  @Test
  void deletionBlockedWhenUsed() {
    Tag t = id(new Tag("Java"), 1);
    when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(t));
    when(repo.countCoursesByTagId(1L)).thenReturn(1L);

    assertThrows(BusinessConflictException.class, () -> service.delete(1L));
    verify(repo, never()).delete(any(Tag.class));
  }

  @Test
  void unusedTagDeleted() {
    Tag t = id(new Tag("Java"), 1);
    when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(t));
    service.delete(1L);

    verify(repo).delete(t);
    verify(repo).flush();
  }

  @Test
  void missingTagRejected() {
    assertThrows(ResourceNotFoundException.class, () -> service.getById(1L));
    assertThrows(ResourceNotFoundException.class, () -> service.delete(1L));
  }

  @Test
  void paginationAndSortValidation() {
    assertThrows(BadRequestException.class, () -> service.findAll(-1, 20, null, "id", "asc"));
    assertThrows(BadRequestException.class, () -> service.findAll(0, 20, null, "unknown", "asc"));
    assertThrows(
        BadRequestException.class, () -> service.findAll(0, 20, "x".repeat(101), "name", "asc"));
  }

  @Test
  void mapsPageAndPreservesSort() {
    var pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id")));
    when(repo.findByNameContainingIgnoreCase("Java", pageable))
        .thenReturn(new PageImpl<>(List.of(id(new Tag("Java"), 1)), pageable, 1));

    var result = service.findAll(0, 20, " Java ", "name", "desc");

    assertEquals("Java", result.content().get(0).name());
    assertEquals(1, result.totalElements());
  }
}
