package org.courseflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {
  private CurrentUser() {}

  public static Long id(Jwt jwt) {
    return Long.valueOf(jwt.getSubject());
  }

  public static boolean hasRole(Authentication authentication, String role) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
  }

  public static boolean isAdmin(Authentication authentication) {
    return hasRole(authentication, "ADMIN");
  }
}
