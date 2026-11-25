package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.module;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.courseflow.assessment.api.QuizAccess;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.dto.ChangeModulePositionRequest;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.ModuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class ModuleServiceCoverageTest {
  private final ModuleRepository repository = mock(ModuleRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final ContentStructure content = mock(ContentStructure.class);
  private final QuizAccess quizzes = mock(QuizAccess.class);
  private final ModuleService service = new ModuleService(repository, guard, content, quizzes);
  private final Module module = module();

  private void setup() {
    when(repository.findByIdAndCourse_Id(50L, 40L)).thenReturn(Optional.of(module));
  }

  @Test
  void findByCourseMapsModules() {
    var page = PageRequest.of(0, 10, Sort.by("position", "id"));
    when(repository.findByCourse_Id(40L, page))
        .thenReturn(new PageImpl<>(List.of(module), page, 1));
    assertEquals(50L, service.findByCourse(40L, 0, 10).content().get(0).id());
    verify(guard).requireExists(40L);
  }

  @Test
  void getByIdMapsModule() {
    setup();
    assertEquals("JPA", service.getById(40L, 50L).title());
  }

  @Test
  void cannotDeleteWithQuiz() {
    setup();
    when(quizzes.hasQuiz(50L)).thenReturn(true);
    assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 20L, false));
    verify(repository, never()).delete(any(Module.class));
  }

  @Test
  void emptyModuleDeleted() {
    setup();
    service.delete(40L, 50L, 20L, false);
    verify(repository).delete(module);
    verify(repository).flush();
  }

  @Test
  void deleteTranslatesForeignKeyConflict() {
    setup();
    doThrow(new DataIntegrityViolationException("related")).when(repository).flush();
    assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 20L, false));
  }

  @Test
  void moveToFreePosition() {
    setup();
    when(repository.findByCourse_IdAndPositionBetweenOrderByPositionAsc(40L, 1, 3))
        .thenReturn(List.of(module));
    assertEquals(
        3,
        service
            .changePosition(40L, 50L, new ChangeModulePositionRequest(3, 0L), 20L, false)
            .position());
    verify(repository).flush();
  }
}
