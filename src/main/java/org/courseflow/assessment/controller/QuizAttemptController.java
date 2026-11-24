package org.courseflow.assessment.controller;

import java.net.URI;

import org.courseflow.security.CurrentUser;
import org.courseflow.assessment.dto.QuizAttemptResponse;
import org.courseflow.assessment.service.QuizAttemptService;
import org.courseflow.common.exception.BusinessConflictException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.courseflow.assessment.dto.SubmitQuizRequest;
import org.courseflow.assessment.dto.QuizAttemptDetailResponse;
import org.courseflow.assessment.entity.QuizSubmissionStatus;
import org.courseflow.common.api.PageResponse;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/quizzes/{quizId}/attempts")
public class QuizAttemptController {

    private final QuizAttemptService quizAttemptService;

    public QuizAttemptController(
            QuizAttemptService quizAttemptService
    ) {
        this.quizAttemptService = quizAttemptService;
    }

    @PostMapping
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Existing resource", useReturnTypeSchema = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Resource created", useReturnTypeSchema = true)
    public ResponseEntity<QuizAttemptResponse> start(
            @PathVariable Long quizId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        QuizAttemptService.StartResult result =
                quizAttemptService.start(
                        quizId,
                        CurrentUser.id(jwt)
                );

        if (result instanceof QuizAttemptService.LimitReached) {
            throw new BusinessConflictException(
                    "Maximum number of Quiz attempts reached"
            );
        }

        if (result instanceof QuizAttemptService.Started started) {
            return ResponseEntity
                    .created(URI.create("/api/quizzes/" + quizId + "/attempts/" + started.attempt().id()))
                    .body(started.attempt());
        }

        if (result instanceof QuizAttemptService.Existing existing) {
            return ResponseEntity.ok(existing.attempt());
        }
        throw new IllegalStateException("Unsupported Quiz start outcome");
    }

    @GetMapping("/{attemptId}")
    public QuizAttemptResponse getMyAttempt(
            @PathVariable Long quizId,
            @PathVariable Long attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quizAttemptService.getMyAttempt(
                quizId,
                attemptId,
                CurrentUser.id(jwt)
        );
    }

    @PostMapping("/{attemptId}/submit")
    public QuizAttemptResponse submit(
            @PathVariable Long quizId,
            @PathVariable Long attemptId,
            @Valid @RequestBody SubmitQuizRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quizAttemptService.submit(
                quizId,
                attemptId,
                CurrentUser.id(jwt),
                request
        ).orElseThrow(() ->
                new BusinessConflictException(
                        "Quiz attempt has expired"
                )
        );
    }

    @GetMapping("/me")
    public PageResponse<QuizAttemptResponse> getMyAttempts(
            @PathVariable Long quizId,
            @RequestParam(required = false)
            QuizSubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "attemptNumber")
            String sortBy,
            @RequestParam(defaultValue = "desc")
            String direction,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quizAttemptService.getMyAttempts(
                quizId,
                CurrentUser.id(jwt),
                status,
                page,
                size,
                sortBy,
                direction
        );
    }

    @GetMapping
    public PageResponse<QuizAttemptResponse> getQuizResults(
            @PathVariable Long quizId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false)
            QuizSubmissionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "takenAt")
            String sortBy,
            @RequestParam(defaultValue = "desc")
            String direction,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        return quizAttemptService.getQuizResults(
                quizId,
                CurrentUser.id(jwt),
                isAdmin,
                studentId,
                status,
                page,
                size,
                sortBy,
                direction
        );
    }

    @GetMapping("/{attemptId}/result")
    public QuizAttemptDetailResponse getMyResult(
            @PathVariable Long quizId,
            @PathVariable Long attemptId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return quizAttemptService.getMyResult(
                quizId,
                attemptId,
                CurrentUser.id(jwt)
        );
    }

    @GetMapping("/{attemptId}/manage")
    public QuizAttemptDetailResponse getResultForManagement(
            @PathVariable Long quizId,
            @PathVariable Long attemptId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        boolean isAdmin = CurrentUser.isAdmin(authentication);

        return quizAttemptService.getResultForManagement(
                quizId,
                attemptId,
                CurrentUser.id(jwt),
                isAdmin
        );
    }

}
