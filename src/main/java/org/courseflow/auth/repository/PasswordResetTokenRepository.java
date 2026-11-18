
package org.courseflow.auth.repository;

import jakarta.persistence.LockModeType;
import org.courseflow.auth.entity.PasswordResetToken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    @Query("""
            select t.userAccount.id
            from PasswordResetToken t
            where t.tokenHash = :tokenHash
            """)
    Optional<Long> findAccountIdByTokenHash(
            @Param("tokenHash") String tokenHash
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from PasswordResetToken t
            where t.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    boolean existsByUserAccount_IdAndUsedAtIsNullAndExpiresAtAfter(
            Long accountId,
            Instant expiresAt
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            update PasswordResetToken t
            set t.usedAt = :usedAt
            where t.userAccount.id = :accountId
              and t.usedAt is null
            """)
    int invalidateUnusedTokensForAccount(
            @Param("accountId") Long accountId,
            @Param("usedAt") Instant usedAt
    );

    @Modifying
    @Query("""
            delete from PasswordResetToken t
            where t.userAccount.id = :accountId
            """)
    int deleteAllByAccountId(
            @Param("accountId") Long accountId
    );
}
