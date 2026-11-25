package org.courseflow.identity.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false, columnDefinition = "bigint default 0")
  private long version;

  public long getVersion() {
    return version;
  }

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(unique = true, length = 254)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "system_admin", nullable = false)
  private boolean systemAdmin = false;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      uniqueConstraints =
          @UniqueConstraint(
              name = "uk_user_roles_user_role",
              columnNames = {"user_id", "role"}))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private Set<UserRole> roles = new HashSet<>();

  protected User() {}

  public User(String displayName, String email, Set<UserRole> roles, Instant now) {
    this.createdAt = now;
    this.displayName = displayName;
    this.email = email;
    this.roles.addAll(roles);
  }

  public Long getId() {
    return id;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public UserStatus getStatus() {
    return status;
  }

  public boolean isSystemAdmin() {
    return systemAdmin;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Set<UserRole> getRoles() {
    return Set.copyOf(roles);
  }

  public void markAsSystemAdmin() {
    if (!roles.contains(UserRole.ADMIN)) {
      throw new IllegalStateException("System administrator must have ADMIN role");
    }

    this.systemAdmin = true;
  }

  public void changeRoles(Set<UserRole> newRoles) {
    if (newRoles == null
        || newRoles.isEmpty()
        || newRoles.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("User must have at least one valid role");
    }

    if (systemAdmin && !newRoles.contains(UserRole.ADMIN)) {
      throw new IllegalStateException("System administrator must retain ADMIN role");
    }

    if (status == UserStatus.DELETED) {
      throw new IllegalStateException("Cannot change roles of a deleted user");
    }

    roles.clear();
    roles.addAll(newRoles);
  }

  public void updateDetails(String newDisplayName, String newEmail) {
    if (status == UserStatus.DELETED) {
      throw new IllegalStateException("Cannot update a deleted user");
    }

    this.displayName = newDisplayName;
    this.email = newEmail;
  }

  public void deactivate() {
    if (systemAdmin) {
      throw new IllegalStateException("Cannot deactivate system administrator");
    }

    if (status == UserStatus.DELETED) {
      throw new IllegalStateException("Cannot deactivate a deleted user");
    }

    this.status = UserStatus.INACTIVE;
  }

  public void activate() {
    if (status == UserStatus.DELETED) {
      throw new IllegalStateException("Cannot reactivate a deleted user");
    }

    this.status = UserStatus.ACTIVE;
  }

  public void markDeleted(Instant now) {
    if (systemAdmin) {
      throw new IllegalStateException("Cannot delete system administrator");
    }

    if (status != UserStatus.INACTIVE) {
      throw new IllegalStateException("User must be inactive before deletion");
    }

    this.status = UserStatus.DELETED;
    this.deletedAt = now;
    this.displayName = "Deleted user";
    this.email = null;
    this.roles.clear();
  }
}
