package org.courseflow.identity.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

  @Query("select distinct u from User u left join fetch u.roles where u.id in :ids")
  List<User> findWithRolesByIds(@Param("ids") List<Long> ids);

  boolean existsByEmail(String email);

  boolean existsBySystemAdminTrue();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            select u
            from User u
            where u.systemAdmin = true
            """)
  Optional<User> findSystemAdminForUpdate();

  @Query(
      """
            select count(distinct u.id)
            from User u
            join u.roles role
            where role = :role
              and u.status = :status
            """)
  long countByRoleAndStatus(@Param("role") UserRole role, @Param("status") UserStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
        select u
        from User u
        where u.id = :id
        """)
  Optional<User> findByIdForUpdate(@Param("id") Long id);
}
