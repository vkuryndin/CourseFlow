package org.courseflow.identity.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;
import org.courseflow.identity.entity.UserStatus;

public record UserResponse(
    Long id,
    long version,
    String displayName,
    String email,
    UserStatus status,
    Set<UserRole> roles,
    boolean systemAdmin) {
  public UserResponse {
    roles = roles == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
  }

  @Override
  public Set<UserRole> roles() {
    return roles == null ? null : Collections.unmodifiableSet(roles);
  }

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getVersion(),
        user.getDisplayName(),
        user.getEmail(),
        user.getStatus(),
        user.getRoles(),
        user.isSystemAdmin());
  }
}
