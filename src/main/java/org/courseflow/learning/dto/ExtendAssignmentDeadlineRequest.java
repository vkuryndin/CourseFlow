package org.courseflow.learning.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record ExtendAssignmentDeadlineRequest(
    @NotNull @Future Instant dueAt, @NotNull @PositiveOrZero Long version) {}
