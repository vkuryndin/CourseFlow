package org.courseflow.catalog.service;

import static org.courseflow.testing.Fixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.courseflow.catalog.entity.Tag;
import org.courseflow.catalog.repository.CourseRepository;
import org.courseflow.catalog.repository.TagRepository;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class CourseTagServiceCoverageTest {
  private final CourseRepository courses = mock(CourseRepository.class);
  private final TagRepository tags = mock(TagRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final CourseTagService service = new CourseTagService(courses, tags, guard);

  @Test
  void courseTagPageMapsTag() {
    when(courses.existsById(40L)).thenReturn(true);
    var page = PageRequest.of(0, 10, Sort.by("name", "id"));
    when(tags.findByCourseId(40L, page))
        .thenReturn(new PageImpl<>(List.of(id(new Tag("JPA"), 90)), page, 1));
    assertEquals("JPA", service.findCourseTags(40L, 0, 10).content().get(0).name());
  }

  @Test
  void missingTagCannotBeAdded() {
    assertThrows(
        ResourceNotFoundException.class, () -> service.addTagToCourse(40L, 90L, 20L, false));
  }

  @Test
  void missingTagCannotBeRemoved() {
    assertThrows(
        ResourceNotFoundException.class, () -> service.removeTagFromCourse(40L, 90L, 20L, false));
  }
}
