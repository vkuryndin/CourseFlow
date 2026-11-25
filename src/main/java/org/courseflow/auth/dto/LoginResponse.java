package org.courseflow.auth.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.courseflow.identity.entity.UserRole;

public record LoginResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    Long userId,
    String displayName,
    Set<UserRole> roles,
    boolean passwordChangeRequired) {
  public LoginResponse {
    roles = roles == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
  }

  @Override
  public Set<UserRole> roles() {
    return roles == null ? null : Collections.unmodifiableSet(roles);
  }
}
