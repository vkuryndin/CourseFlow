
package org.courseflow.learning.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateAssignmentRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotBlank
        @Size(max = 20000)
        String description,

        @NotNull
        @Future
        Instant dueAt,

        @NotNull
        @Positive
        Integer maxScore

) {
}
