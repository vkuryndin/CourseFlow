package org.courseflow.content.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLessonRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 20000) String content,
    @Size(max = 2048) String videoUrl,
    @NotNull @Min(1) Integer position) {}
