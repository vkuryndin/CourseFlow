package org.courseflow.identity.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 2000) String bio, @Size(max = 2048) String avatarUrl) {}
