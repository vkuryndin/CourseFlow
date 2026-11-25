package org.courseflow.content.dto;

public record LessonSummaryResponse(
    Long id, long version, Long moduleId, String title, Integer position) {}
