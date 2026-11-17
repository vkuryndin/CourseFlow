
package org.courseflow.security;

import org.courseflow.auth.repository.AuthSessionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuthSessionStartupCleanup implements ApplicationRunner {

    private final AuthSessionRepository sessionRepository;

    public AuthSessionStartupCleanup(
            AuthSessionRepository sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        sessionRepository.deleteAllInBatch();
    }
}
