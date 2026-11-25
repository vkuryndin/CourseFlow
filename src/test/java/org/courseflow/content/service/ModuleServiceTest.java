package org.courseflow.content.service;

import static org.courseflow.testing.Fixtures.id;
import static org.courseflow.testing.Fixtures.module;
import static org.courseflow.testing.Fixtures.quiz;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.courseflow.assessment.api.QuizAccess;
import org.courseflow.catalog.service.CourseGuard;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.common.exception.ResourceNotFoundException;
import org.courseflow.content.api.ContentStructure;
import org.courseflow.content.dto.ChangeModulePositionRequest;
import org.courseflow.content.dto.CreateModuleRequest;
import org.courseflow.content.dto.UpdateModuleRequest;
import org.courseflow.content.entity.Module;
import org.courseflow.content.repository.ModuleRepository;
import org.junit.jupiter.api.Test;

class ModuleServiceTest {
  private final ModuleRepository repo = mock(ModuleRepository.class);
  private final CourseGuard guard = mock(CourseGuard.class);
  private final ContentStructure content = mock(ContentStructure.class);
  private final QuizAccess quiz = mock(QuizAccess.class);
  private final ModuleService service = new ModuleService(repo, guard, content, quiz);
  private final Module m = module();
  private final org.courseflow.catalog.entity.Course c = m.getCourse();

  private void setup() {
    when(guard.lockEditable(eq(40L), eq(20L), eq(false), anyString())).thenReturn(c);
    when(repo.findByIdAndCourse_Id(50L, 40L)).thenReturn(Optional.of(m));
  }

  @Test
  void createsModuleInFreePosition() {
    setup();
    when(repo.saveAndFlush(any(Module.class))).thenAnswer((i) -> id(i.getArgument(0), 51));
    var r = service.create(40L, new CreateModuleRequest(" New ", " Text ", 2), 20L, false);

    assertEquals("New", r.title());
    assertEquals(2, r.position());
  }

  @Test
  void occupiedPositionRejected() {
    setup();
    when(repo.existsByCourse_IdAndPosition(40L, 1)).thenReturn(true);

    assertThrows(
        BusinessConflictException.class,
        () -> service.create(40L, new CreateModuleRequest("New", null, 1), 20L, false));
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void updateAndNoOpMove() {
    setup();

    assertEquals(
        "New",
        service.update(40L, 50L, new UpdateModuleRequest(" New ", null, 0L), 20L, false).title());
    assertEquals(
        1,
        service
            .changePosition(40L, 50L, new ChangeModulePositionRequest(1, 0L), 20L, false)
            .position());
    verify(repo).flush();
  }

  @Test
  void missingModuleAndDeletionDependencies() {
    setup();

    assertThrows(ResourceNotFoundException.class, () -> service.getById(40L, 51L));
    when(content.hasLessons(50L)).thenReturn(true, false);
    when(quiz.hasQuiz(50L)).thenReturn(true, false);
    assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 20L, false));
    assertThrows(BusinessConflictException.class, () -> service.delete(40L, 50L, 20L, false));
    service.delete(40L, 50L, 20L, false);
    verify(repo).delete(m);
  }
}
