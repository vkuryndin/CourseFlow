
package org.courseflow.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TagRequest(
        Long version,
        @NotBlank
        @Size(max = 50)
        String name
) {
}
