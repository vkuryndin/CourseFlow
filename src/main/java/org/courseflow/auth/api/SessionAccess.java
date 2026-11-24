package org.courseflow.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.identity.entity.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Session verification and the agreed startup invalidation policy. */
@Service
public class SessionAccess {
    private final AuthSessionRepository sessions;

    public SessionAccess(AuthSessionRepository sessions) { this.sessions = sessions; }

    @Transactional(readOnly = true)
    public List<String> activeRoles(UUID sessionId, Long userId, Instant now, boolean passwordChangeRequired) {
        return sessions.findValidSessionRoles(sessionId, userId, UserStatus.ACTIVE, now, passwordChangeRequired)
                .stream().map(Enum::name).sorted().toList();
    }

    @Transactional
    public void invalidateAllOnStartup() { sessions.deleteAllInBatch(); }
}
