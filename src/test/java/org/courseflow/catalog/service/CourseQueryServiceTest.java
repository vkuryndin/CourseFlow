package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.category;
import static org.courseflow.testing.Fixtures.course;
import static org.courseflow.testing.Fixtures.teacher;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
import org.courseflow.catalog.entity.Course;
import org.courseflow.catalog.entity.CourseStatus;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class CourseQueryServiceTest {
  private final CourseRepository repository = mock(CourseRepository.class);
  private final CourseQueryService service = new CourseQueryService(repository);

  @SuppressWarnings("unchecked")
  private void stubPage() {
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenAnswer(invocation -> new PageImpl<>(List.of(course()), invocation.getArgument(1), 1));
  }

  @SuppressWarnings("unchecked")
  private Specification<Course> capturedSpecification() {
    ArgumentCaptor<Specification<Course>> captor =
        ArgumentCaptor.forClass((Class) Specification.class);
    verify(repository).findAll(captor.capture(), any(Pageable.class));
    return captor.getValue();
  }

  @Test
  void getExistingCourse() {
    when(repository.findById(40L)).thenReturn(Optional.of(course()));
    assertEquals(40L, service.getById(40L).id());
  }

  @Test
  void missingCourseRejected() {
    assertThrows(ResourceNotFoundException.class, () -> service.getById(40L));
  }

  @Test
  void invalidCategoryFilter() {
    assertThrows(
        BadRequestException.class, () -> service.findAll(0, 10, null, 0L, null, null, "id", "asc"));
    verifyNoInteractions(repository);
  }

  @Test
  void invalidTeacherFilter() {
    assertThrows(
        BadRequestException.class,
        () -> service.findAll(0, 10, null, null, -1L, null, "id", "asc"));
    verifyNoInteractions(repository);
  }

  @Test
  void overlongSearchRejected() {
    assertThrows(
        BadRequestException.class,
        () -> service.findAll(0, 10, "x".repeat(201), null, null, null, "id", "asc"));
  }

  @Test
  void invalidSortRejected() {
    assertThrows(
        BadRequestException.class,
        () -> service.findAll(0, 10, null, null, null, null, "email", "asc"));
  }

  @Test
  void unfilteredPageUsesNoPredicates() {
    stubPage();
    var result = service.findAll(0, 10, null, null, null, null, "id", "asc");
    assertEquals(40L, result.content().get(0).id());
    @SuppressWarnings("unchecked")
    Root<Course> root = mock(Root.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    capturedSpecification().toPredicate(root, mock(CriteriaQuery.class), builder);
    verify(builder).and(new Predicate[0]);
    verifyNoInteractions(root);
  }

  @Test
  void allFiltersBuildLiteralSearchAndForeignKeys() {
    stubPage();
    service.findAll(0, 10, " ORM ", 30L, 20L, CourseStatus.ACTIVE, "title", "desc");
    @SuppressWarnings("unchecked")
    Root<Course> root = mock(Root.class);
    CriteriaBuilder builder = mock(CriteriaBuilder.class);
    @SuppressWarnings("unchecked")
    Path<String> title = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<String> description = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Object> category = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Object> teacher = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Long> categoryPath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<Long> teacherPath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Path<CourseStatus> statusPath = mock(Path.class);
    @SuppressWarnings("unchecked")
    Expression<String> titleLower = mock(Expression.class);
    @SuppressWarnings("unchecked")
    Expression<String> descriptionLower = mock(Expression.class);
    when(root.<String>get("title")).thenReturn(title);
    when(root.<String>get("description")).thenReturn(description);
    when(root.<Object>get("category")).thenReturn(category);
    when(root.<Object>get("teacher")).thenReturn(teacher);
    when(root.<CourseStatus>get("status")).thenReturn(statusPath);
    when(category.<Long>get("id")).thenReturn(categoryPath);
    when(teacher.<Long>get("id")).thenReturn(teacherPath);
    when(builder.lower(title)).thenReturn(titleLower);
    when(builder.lower(description)).thenReturn(descriptionLower);
    capturedSpecification().toPredicate(root, mock(CriteriaQuery.class), builder);
    verify(builder).like(titleLower, "%orm%", '\\');
    verify(builder).like(descriptionLower, "%orm%", '\\');
    verify(builder).equal(categoryPath, 30L);
    verify(builder).equal(teacherPath, 20L);
    verify(builder).equal(statusPath, CourseStatus.ACTIVE);
  }
}
