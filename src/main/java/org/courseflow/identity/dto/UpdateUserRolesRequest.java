package org.courseflow.identity.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.courseflow.identity.entity.UserRole;

public record UpdateUserRolesRequest(
    @NotEmpty Set<@NotNull UserRole> roles,
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero
        Long version) {}
