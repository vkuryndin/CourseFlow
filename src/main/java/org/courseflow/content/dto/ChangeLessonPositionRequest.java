
package org.courseflow.content.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ChangeLessonPositionRequest(

        @NotNull
        @Min(1)
        Integer position
) {
}
