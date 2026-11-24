package org.courseflow.auth.repository;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.courseflow.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository
        extends JpaRepository<AuthSession, UUID> {

    // Scalar projection: never load the account password hash or a graph of entities.
    @Query("""
            select role from AuthSession s
            join s.userAccount a
            join a.user u
            join u.roles role
            where s.id = :sessionId
              and u.id = :userId
              and u.status = :status
              and s.revoked = false
              and s.expiresAt > :now
            """)
    List<UserRole> findValidSessionRoles(@Param("sessionId") UUID sessionId,
                                         @Param("userId") Long userId,
                                         @Param("status") UserStatus status,
                                         @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthSession s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    List<AuthSession> findAllByUserAccount_User_IdAndRevokedFalse(
            Long userId
    );

    @Modifying
    @Query("""
        delete from AuthSession s
        where s.userAccount.id = :accountId
        """)
    int deleteAllByAccountId(@Param("accountId") Long accountId);

    // Caller must hold the account lock and an active transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    default void revokeActiveSessionsByUserId(Long userId) {
        findAllByUserAccount_User_IdAndRevokedFalse(userId).forEach(AuthSession::revoke);
    }
}
