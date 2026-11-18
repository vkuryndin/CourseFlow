
package org.courseflow.identity.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.courseflow.identity.entity.UserRole;

import java.util.Set;

public record UpdateUserRolesRequest(

        @NotEmpty
        Set<@NotNull UserRole> roles

) {
}
