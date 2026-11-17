
package org.courseflow.content.repository;

import org.courseflow.content.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository
        extends JpaRepository<Lesson, Long> {

    Page<Lesson> findAllByModule_Id(
            Long moduleId,
            Pageable pageable
    );

    boolean existsByModule_Id(Long moduleId);
}
