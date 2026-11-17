
package org.courseflow.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotBlank(message = "Category description is required")
        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description

) {
}
