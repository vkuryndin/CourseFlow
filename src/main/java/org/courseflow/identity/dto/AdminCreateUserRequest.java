
package org.courseflow.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.courseflow.identity.entity.UserRole;

import java.util.Set;

public record AdminCreateUserRequest(

        @NotBlank
        @Size(max = 120)
        String displayName,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotEmpty
        Set<@NotNull UserRole> roles

) {
}
