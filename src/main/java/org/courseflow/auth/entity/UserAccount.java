package org.courseflow.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.courseflow.identity.entity.User;

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
  private Instant createdAt;

  protected UserAccount() {}

  public UserAccount(User user, String passwordHash, Instant now) {
    this.createdAt = now;
    this.user = user;
    this.passwordHash = passwordHash;
  }

  public void requirePasswordChange() {
    this.passwordChangeRequired = true;
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

  public void replacePasswordHash(String newPasswordHash) {
    if (newPasswordHash == null || newPasswordHash.isBlank()) {
      throw new IllegalArgumentException("Password hash must not be blank");
    }

    this.passwordHash = newPasswordHash;
    this.passwordChangeRequired = false;
  }
}
