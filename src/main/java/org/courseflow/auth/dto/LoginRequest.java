
package org.courseflow.auth.dto;

import org.courseflow.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Password is required")
        @Size(max = 72)
        @MaxUtf8Bytes(72)
        String password

) {
}
