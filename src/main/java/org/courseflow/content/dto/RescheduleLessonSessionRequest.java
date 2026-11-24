
package org.courseflow.content.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleLessonSessionRequest(

        @NotNull
        Instant startsAt,

        @NotNull
        Instant endsAt,

        @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.PositiveOrZero
        Long version

) {
}
