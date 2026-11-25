package org.courseflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.courseflow.common.validation.MaxUtf8Bytes;

public record RegistrationRequest(
    @NotBlank(message = "Display name is required")
        @Size(max = 120, message = "Display name must not exceed 120 characters")
        String displayName,
    @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,
    @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters")
        @MaxUtf8Bytes(72)
        String password) {}
