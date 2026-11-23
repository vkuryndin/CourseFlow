
package org.courseflow.content.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleLessonSessionRequest(

        @NotNull
        Instant startsAt,

        @NotNull
        Instant endsAt

) {
}
