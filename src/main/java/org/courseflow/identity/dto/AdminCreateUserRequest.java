package org.courseflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.courseflow.common.validation.MaxUtf8Bytes;
import org.courseflow.identity.entity.UserRole;

public record AdminCreateUserRequest(
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 72) @MaxUtf8Bytes(72) String password,
    @NotEmpty Set<@NotNull UserRole> roles,
    @io.swagger.v3.oas.annotations.media.Schema(
            description =
                "Require password change on first login; defaults to true for administrator-created accounts",
            defaultValue = "true")
        Boolean passwordChangeRequired) {}
