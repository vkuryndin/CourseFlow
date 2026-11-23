
package org.courseflow.catalog.repository;

import org.courseflow.catalog.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository
        extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    boolean existsByName(String name);

    Page<Tag> findByNameContainingIgnoreCase(
            String name,
            Pageable pageable
    );

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(
            String name,
            Long id
    );

    @Query("""
        select count(c)
        from Course c
        join c.tags t
        where t.id = :tagId
        """)
    long countCoursesByTagId(@Param("tagId") Long tagId);



    @Query(
            value = """
                SELECT t
                FROM Tag t
                WHERE t.id IN (
                    SELECT ct.id
                    FROM Course c
                    JOIN c.tags ct
                    WHERE c.id = :courseId
                )
                """,
            countQuery = """
                SELECT COUNT(t)
                FROM Tag t
                WHERE t.id IN (
                    SELECT ct.id
                    FROM Course c
                    JOIN c.tags ct
                    WHERE c.id = :courseId
                )
                """
    )
    Page<Tag> findByCourseId(
            @Param("courseId") Long courseId,
            Pageable pageable
    );



}
