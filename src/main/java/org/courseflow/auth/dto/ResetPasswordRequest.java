package org.courseflow.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.courseflow.common.validation.MaxUtf8Bytes;

public record ResetPasswordRequest(
    @NotBlank @Size(max = 128) String token,
    @NotBlank
        @Size(min = 8, max = 72)
        @MaxUtf8Bytes(value = 72, message = "New password must not exceed 72 UTF-8 bytes")
        String newPassword) {}
