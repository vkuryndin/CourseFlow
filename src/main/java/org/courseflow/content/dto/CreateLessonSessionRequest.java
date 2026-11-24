
package org.courseflow.content.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.courseflow.content.entity.LessonSessionFormat;

import java.time.Instant;

public record CreateLessonSessionRequest(

        @NotNull
        Instant startsAt,

        @NotNull
        Instant endsAt,

        @NotNull
        LessonSessionFormat format,

        @Size(max = 2048)
        String meetingUrl,

        @io.swagger.v3.oas.annotations.media.Schema(description = "Required on PUT; omit on POST")
        @jakarta.validation.constraints.PositiveOrZero
        Long version

) {
}
