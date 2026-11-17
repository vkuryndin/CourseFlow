
package org.courseflow.auth.entity;

import jakarta.persistence.*;
import org.courseflow.identity.entity.User;

import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserAccount() {
    }

    public UserAccount(User user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangeRequired = false;
    }

    public void requirePasswordChange() {
        this.passwordChangeRequired = true;
    }
}
