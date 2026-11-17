
package org.courseflow.content.repository;

import org.courseflow.content.entity.Module;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository
        extends JpaRepository<Module, Long> {

    Page<Module> findAllByCourse_Id(
            Long courseId,
            Pageable pageable
    );

    boolean existsByCourse_Id(Long courseId);
}
