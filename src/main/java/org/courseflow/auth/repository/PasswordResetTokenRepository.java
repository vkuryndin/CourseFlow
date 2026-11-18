
package org.courseflow.auth.repository;

import org.courseflow.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    @Modifying
    @Query("""
        delete from PasswordResetToken t
        where t.userAccount.id = :accountId
        """)
    int deleteAllByAccountId(@Param("accountId") Long accountId);

}
