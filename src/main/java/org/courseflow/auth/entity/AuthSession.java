package org.courseflow.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_account_id", nullable = false)
  private UserAccount userAccount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean revoked = false;

  protected AuthSession() {}

  public AuthSession(UserAccount userAccount, Instant expiresAt, Instant now) {
    this.createdAt = now;
    this.userAccount = userAccount;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UserAccount getUserAccount() {
    return userAccount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isRevoked() {
    return revoked;
  }

  public boolean isActive(Instant now) {
    return !revoked && now.isBefore(expiresAt);
  }

  public void revoke() {
    this.revoked = true;
  }
}
