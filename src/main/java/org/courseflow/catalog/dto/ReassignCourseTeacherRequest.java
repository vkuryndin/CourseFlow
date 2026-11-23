
package org.courseflow.catalog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReassignCourseTeacherRequest(
        @NotNull
        @Positive
        Long teacherId
) {
}
