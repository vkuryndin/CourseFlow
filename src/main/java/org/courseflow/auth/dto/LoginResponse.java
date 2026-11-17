
package org.courseflow.auth.dto;

import org.courseflow.identity.entity.UserRole;

import java.time.Instant;
import java.util.Set;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        Long userId,
        String displayName,
        Set<UserRole> roles,
        boolean passwordChangeRequired
) {
}
