
package org.courseflow.content.repository;

import org.courseflow.content.entity.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface LessonRepository
        extends JpaRepository<Lesson, Long> {

    Page<Lesson> findAllByModule_Id(
            Long moduleId,
            Pageable pageable
    );

    boolean existsByModule_Id(Long moduleId);

    Page<Lesson> findByModule_Id(
            Long moduleId,
            Pageable pageable
    );

    Optional<Lesson> findByIdAndModule_Id(
            Long id,
            Long moduleId
    );

    boolean existsByModule_IdAndPosition(
            Long moduleId,
            Integer position
    );


    Optional<Lesson> findByModule_IdAndPosition(
            Long moduleId,
            Integer position
    );

    Optional<Lesson> findFirstByModule_IdOrderByPositionDescIdDesc(
            Long moduleId
    );

}
