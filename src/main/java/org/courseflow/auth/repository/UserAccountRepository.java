package org.courseflow.auth.repository;

import org.courseflow.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUser_Id(Long userId);

    Optional<UserAccount> findByUser_Email(String email);

    Optional<UserAccount> findByUser_EmailIgnoreCase(String email);

    // Read only the ID so the account is not loaded into the persistence
    // context before acquiring the write lock.
    @Query("""
        select a.id
        from UserAccount a
        where a.user.email = :email
        """)
    Optional<Long> findAccountIdByUserEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from UserAccount a
        where a.user.id = :userId
        """)
    Optional<UserAccount> findByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select a
        from UserAccount a
        where a.id = :id
        """)
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);


}
