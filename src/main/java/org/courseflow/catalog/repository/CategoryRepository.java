package org.courseflow.catalog.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.courseflow.catalog.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

  Optional<Category> findByName(String name);

  @Query("select (count(c) > 0) from Category c where lower(c.name) = lower(:name)")
  boolean existsByNameIgnoreCase(@Param("name") String name);

  @Query("select (count(c) > 0) from Category c where lower(c.name) = lower(:name) and c.id <> :id")
  boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Category c where c.id = :id")
  Optional<Category> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select c from Category c where c.id = :id")
  Optional<Category> findByIdForShare(@Param("id") Long id);

  Page<Category> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
