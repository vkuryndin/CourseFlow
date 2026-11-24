
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

    @Query("select (count(t) > 0) from Tag t where lower(t.name) = lower(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("select (count(t) > 0) from Tag t where lower(t.name) = lower(:name) and t.id <> :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name,
                                           @Param("id") Long id);

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
