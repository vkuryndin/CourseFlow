package org.courseflow.auth.service;

import java.time.Clock;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Service
public class SystemAdminBootstrapService {

    private final Clock clock;

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${COURSEFLOW_ADMIN_EMAIL:}")
    private String adminEmail;

    @Value("${COURSEFLOW_ADMIN_PASSWORD:}")
    private String adminPassword;

    public SystemAdminBootstrapService(
            UserRepository userRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.clock = clock;
        this.userRepository = userRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void initialize() {
        if (userRepository.existsBySystemAdminTrue()) {
            return;
        }

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Configure COURSEFLOW_ADMIN_EMAIL and "
                            + "COURSEFLOW_ADMIN_PASSWORD"
            );
        }

        String email = EmailAddresses.normalize(adminEmail);

        int passwordBytes = adminPassword
                .getBytes(StandardCharsets.UTF_8).length;

        if (adminPassword.length() < 8 || passwordBytes > 72) {
            throw new IllegalStateException(
                    "System ADMIN password must contain at least "
                            + "8 characters and at most 72 UTF-8 bytes"
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "System ADMIN email is already used"
            );
        }

        User admin = new User(
                "System Administrator",
                email,
                Set.of(UserRole.ADMIN),
                clock.instant()
        );

        admin.markAsSystemAdmin();
        userRepository.saveAndFlush(admin);

        UserAccount account = new UserAccount(
                admin,
                passwordEncoder.encode(adminPassword),
                clock.instant()
        );

        userAccountRepository.saveAndFlush(account);
    }
}
