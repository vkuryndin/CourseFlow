package org.courseflow.assessment.api;

import org.courseflow.assessment.repository.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle checks exposed by the owning module; the caller retains its parent lock. */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class QuizAccess {
  private final QuizRepository repository;

  public QuizAccess(QuizRepository repository) {
    this.repository = repository;
  }

  public boolean hasQuiz(Long moduleId) {
    return repository.existsByModule_Id(moduleId);
  }
}
