package org.courseflow.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateLessonRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 20000) String content,
    @Size(max = 2048) String videoUrl,
    @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.PositiveOrZero
        Long version) {}
