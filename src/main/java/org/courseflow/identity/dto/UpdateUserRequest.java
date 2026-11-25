package org.courseflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @NotBlank @Size(max = 120) String displayName,
    @NotBlank @Email @Size(max = 254) String email,
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero
        Long version) {}
