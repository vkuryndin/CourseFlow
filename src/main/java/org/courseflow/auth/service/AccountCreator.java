package org.courseflow.auth.service;

import java.time.Clock;
import java.util.Set;
import java.util.function.Function;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.api.UserDirectory;
import org.springframework.stereotype.Service;
import org.courseflow.common.validation.EmailAddresses;
import org.courseflow.common.validation.PasswordBytes;
import org.courseflow.common.exception.BusinessConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountCreator {
    private final UserDirectory userDirectory;
    private final UserAccountRepository accounts;
    private final Clock clock;

    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;

    public AccountCreator(UserDirectory userDirectory, UserAccountRepository accounts, Clock clock,
                          PasswordEncoder passwordEncoder, PlatformTransactionManager transactionManager) {
        this.userDirectory = userDirectory;
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
        if (userDirectory.emailInUse(email)) {
            throw new BusinessConflictException("Email is already registered");
        }
    }

    private User persist(String displayName, String normalizedEmail, Set<UserRole> roles,
                         String passwordHash, boolean passwordChangeRequired) {
        User user = userDirectory.createUser(displayName, normalizedEmail, roles, clock.instant());
        UserAccount account = new UserAccount(user, passwordHash, clock.instant());
        if (passwordChangeRequired) account.requirePasswordChange();
        accounts.saveAndFlush(account);
        return user;
    }
}
