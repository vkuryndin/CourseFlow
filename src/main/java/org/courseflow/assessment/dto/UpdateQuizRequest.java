
package org.courseflow.assessment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateQuizRequest(

        @NotBlank
        @Size(max = 200)
        String title,

        @NotNull
        @Min(1)
        @Max(240)
        Integer timeLimit,

        @NotNull
        @Min(1)
        @Max(10)
        Integer maxAttempts

) {
}
