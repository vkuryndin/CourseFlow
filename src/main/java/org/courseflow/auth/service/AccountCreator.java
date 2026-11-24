package org.courseflow.auth.service;

import java.time.Clock;
import java.util.Set;
import java.util.function.Function;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.common.validation.PasswordBytes;
import org.courseflow.common.exception.BusinessConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountCreator {
    private final UserRepository users;
    private final UserAccountRepository accounts;
    private final Clock clock;

    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public AccountCreator(UserRepository users, UserAccountRepository accounts, Clock clock,
                          PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.accounts = accounts;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public <T> T create(String displayName, String email, Set<UserRole> roles,
                        String password, boolean passwordChangeRequired, Function<User, T> responseMapper) {
        String normalizedEmail = EmailAddresses.normalize(email);
        PasswordBytes.requireFits(password, "Password");
        requireAvailableEmail(normalizedEmail);
        String passwordHash = passwordEncoder.encode(password);
        return transaction.execute(status -> {
            requireAvailableEmail(normalizedEmail);
            return responseMapper.apply(persist(displayName.trim(), normalizedEmail, roles,
                    passwordHash, passwordChangeRequired));
        });
    }

    private void requireAvailableEmail(String email) {
        if (users.existsByEmail(email)) {
            throw new BusinessConflictException("Email is already registered");
        }
    }

    private User persist(String displayName, String normalizedEmail, Set<UserRole> roles,
                         String passwordHash, boolean passwordChangeRequired) {
        User user = users.saveAndFlush(new User(displayName, normalizedEmail, roles, clock.instant()));
        UserAccount account = new UserAccount(user, passwordHash, clock.instant());
        if (passwordChangeRequired) account.requirePasswordChange();
        accounts.saveAndFlush(account);
        return user;
    }
}
