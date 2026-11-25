package org.courseflow.auth.dto;

import java.util.Set;
import org.courseflow.identity.entity.User;
import org.courseflow.identity.entity.UserRole;

public record RegistrationResponse(Long id, String displayName, String email, Set<UserRole> roles) {

  public static RegistrationResponse from(User user) {
    return new RegistrationResponse(
        user.getId(), user.getDisplayName(), user.getEmail(), user.getRoles());
  }
}
