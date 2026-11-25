package org.courseflow.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubmissionRequest(@NotBlank @Size(max = 20000) String content) {}
