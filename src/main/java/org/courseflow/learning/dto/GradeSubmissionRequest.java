
package org.courseflow.learning.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GradeSubmissionRequest(

        @NotNull
        @Min(0)
        Integer score,

        @Size(max = 5000)
        String feedback

) {
}
