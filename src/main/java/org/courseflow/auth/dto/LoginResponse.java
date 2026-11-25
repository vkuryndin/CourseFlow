package org.courseflow.auth.dto;

import java.time.Instant;
import java.util.Set;
import org.courseflow.identity.entity.UserRole;

public record LoginResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    Long userId,
    String displayName,
    Set<UserRole> roles,
    boolean passwordChangeRequired) {}
