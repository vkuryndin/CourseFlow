
package org.courseflow.auth.repository;

import org.courseflow.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository
        extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findAllByUserAccount_IdAndRevokedFalse(
            Long userAccountId
    );

    List<AuthSession> findAllByUserAccount_User_IdAndRevokedFalse(
            Long userId
    );

    @Modifying
    @Query("""
        delete from AuthSession s
        where s.userAccount.id = :accountId
        """)
    int deleteAllByAccountId(@Param("accountId") Long accountId);


}
