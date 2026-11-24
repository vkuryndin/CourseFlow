package org.courseflow.learning.api;

import org.courseflow.learning.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lifecycle checks exposed by the owning module; the caller retains its parent lock. */
@Service
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class AssignmentAccess {
    private final AssignmentRepository repository;

    public AssignmentAccess(AssignmentRepository repository) { this.repository = repository; }

    public boolean hasAssignments(Long lessonId) { return repository.existsByLesson_Id(lessonId); }
}
