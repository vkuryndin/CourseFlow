
package org.courseflow.auth.repository;

import org.courseflow.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthSessionRepository
        extends JpaRepository<AuthSession, UUID> {

    List<AuthSession> findAllByUserAccount_IdAndRevokedFalse(
            Long userAccountId
    );
}
