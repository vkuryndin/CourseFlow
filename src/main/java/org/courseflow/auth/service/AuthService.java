package org.courseflow.auth.service;

import org.courseflow.auth.dto.LoginRequest;
import org.courseflow.auth.dto.LoginResponse;
import org.courseflow.auth.dto.RegistrationRequest;
import org.courseflow.auth.dto.RegistrationResponse;
import org.courseflow.auth.entity.AuthSession;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.AuthSessionRepository;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.common.exception.BadRequestException;
import org.courseflow.common.exception.BusinessConflictException;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;
import org.courseflow.identity.repository.UserRepository;
import org.courseflow.security.JwtService;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            UserAccountRepository userAccountRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.userAccountRepository = userAccountRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegistrationResponse register(
            RegistrationRequest request
    ) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessConflictException(
                    "Email is already registered"
            );
        }

        if (request.password()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException(
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        User user = new User(
                request.displayName().trim(),
                email,
                Set.of(UserRole.STUDENT)
        );

        User savedUser = userRepository.saveAndFlush(user);

        String passwordHash =
                passwordEncoder.encode(request.password());

        UserAccount account = new UserAccount(
                savedUser,
                passwordHash
        );

        userAccountRepository.saveAndFlush(account);

        return RegistrationResponse.from(savedUser);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        Long accountId = userAccountRepository
                .findAccountIdByUserEmail(email)
                .orElseThrow(this::invalidCredentials);

        // Serialize login with password changes and resets. Read the
        // password hash only after obtaining the account lock.
        UserAccount account = userAccountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(this::invalidCredentials);

        User user = account.getUser();

        if (!email.equals(user.getEmail())) {
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(
                request.password(),
                account.getPasswordHash()
        )) {
            throw invalidCredentials();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }

        if (user.getRoles().isEmpty()) {
            throw invalidCredentials();
        }

        Instant expiresAt = Instant.now()
                .plus(1, ChronoUnit.HOURS);

        AuthSession session = new AuthSession(
                account,
                expiresAt
        );

        AuthSession savedSession =
                authSessionRepository.saveAndFlush(session);

        String accessToken =
                jwtService.generateAccessToken(savedSession);

        return new LoginResponse(
                accessToken,
                "Bearer",
                savedSession.getExpiresAt(),
                user.getId(),
                user.getDisplayName(),
                user.getRoles(),
                account.isPasswordChangeRequired()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException(
                "Invalid email or password"
        );
    }
}
