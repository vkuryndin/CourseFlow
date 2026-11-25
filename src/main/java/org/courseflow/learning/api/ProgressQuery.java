package org.courseflow.learning.api;

import java.time.Instant;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.CourseProgressSummaryResponse;

/** Read-only progress contract used by the student dashboard. */
public interface ProgressQuery {
  PageResponse<CourseProgressSummaryResponse> getDashboardProgress(
      Long studentId, int size, Instant now);
}
