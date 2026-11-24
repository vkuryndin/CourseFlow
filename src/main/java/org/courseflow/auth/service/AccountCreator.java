package org.courseflow.auth.service;

import java.time.Clock;
import java.util.Set;
import org.courseflow.auth.entity.UserAccount;
import org.courseflow.auth.repository.UserAccountRepository;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountCreator {
    private final UserRepository users;
    private final UserAccountRepository accounts;
    private final Clock clock;

    public AccountCreator(UserRepository users, UserAccountRepository accounts, Clock clock) {
        this.users = users;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public User create(String displayName, String normalizedEmail, Set<UserRole> roles,
                       String passwordHash, boolean passwordChangeRequired) {
        User user = users.saveAndFlush(new User(displayName, normalizedEmail, roles, clock.instant()));
        UserAccount account = new UserAccount(user, passwordHash, clock.instant());
        if (passwordChangeRequired) account.requirePasswordChange();
        accounts.saveAndFlush(account);
        return user;
    }
}
