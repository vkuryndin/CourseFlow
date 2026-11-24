package org.courseflow.learning.controller;

import org.courseflow.assessment.dto.MyQuizAttemptResponse;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.common.api.PageResponse;
import org.courseflow.learning.dto.MyAssignmentResponse;
import org.courseflow.learning.dto.MySubmissionResponse;
import org.courseflow.learning.entity.SubmissionStatus;
import org.courseflow.learning.service.StudentLearningService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class StudentLearningController {
    private final StudentLearningService studentLearningService;

    public StudentLearningController(StudentLearningService studentLearningService) {
        this.studentLearningService = studentLearningService;
    }

    @GetMapping("/assignments")
    public PageResponse<MyAssignmentResponse> getAssignments(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) MyAssignmentResponse.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return studentLearningService.getAssignments(Long.valueOf(jwt.getSubject()), courseId, status, page, size);
    }

    @GetMapping("/submissions")
    public PageResponse<MySubmissionResponse> getSubmissions(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return studentLearningService.getSubmissions(Long.valueOf(jwt.getSubject()), courseId, status, page, size);
    }

    @GetMapping("/quiz-attempts")
    public PageResponse<MyQuizAttemptResponse> getQuizAttempts(
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) QuizSubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt) {
        return studentLearningService.getQuizAttempts(Long.valueOf(jwt.getSubject()), courseId, status, page, size);
    }
}
