package org.courseflow.learning.repository;

/** Shared HQL expressions for analytical queries. */
final class AssessmentQueryExpressions {
  private AssessmentQueryExpressions() {}

  // The same aggregation is used in both read models (alias s and parameter now).
  static final String ACTIVE_ATTEMPT_COUNT =
      """
            SUM(CASE WHEN s.status = org.courseflow.assessment.entity.QuizSubmissionStatus.IN_PROGRESS
                       AND s.expiresAt > :now THEN 1 ELSE 0 END)
            """;
}
