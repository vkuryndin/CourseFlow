
package org.courseflow.common.exception;

import java.time.Clock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.courseflow.common.api.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.springframework.web.servlet.HandlerMapping;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern == null ? "unmapped" : pattern.toString();
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request, "RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            BadRequestException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request, "BAD_REQUEST");
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(
            BusinessConflictException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.CONFLICT,
                ex.getMessage(),
                request, "BUSINESS_CONFLICT");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                request, "ACCESS_DENIED");
    }

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidSession(InvalidSessionException ex, HttpServletRequest request) {
        ApiErrorResponse body = buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request,
                "INVALID_SESSION").getBody();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        // Never echo invalid values (they can be passwords or tokens).
        return buildResponse(HttpStatus.BAD_REQUEST, "Request validation failed", request, "VALIDATION_FAILED");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex,
            HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Missing header: " + ex.getHeaderName(), request, "MISSING_HEADER");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request, "INVALID_CREDENTIALS");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField()
                        + ": " + error.getDefaultMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Request validation failed";
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message,
                request, "VALIDATION_FAILED");
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameters",
                request, "VALIDATION_FAILED");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid or missing JSON request body",
                request, "INVALID_JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid value for parameter: " + ex.getName(),
                request, "INVALID_PARAMETER");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Missing parameter: " + ex.getParameterName(),
                request, "MISSING_PARAMETER");
    }

    @ExceptionHandler({
            org.springframework.dao.ConcurrencyFailureException.class,
            jakarta.persistence.OptimisticLockException.class,
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConcurrentConflict(
            Exception ex, HttpServletRequest request
    ) {
        log.warn("Concurrent operation conflict for {}: {}",
                route(request), ex.getClass().getSimpleName());
        return buildResponse(HttpStatus.CONFLICT,
                "Concurrent operation conflict; reload and retry if appropriate", request, "CONCURRENT_CONFLICT");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(
            DataIntegrityViolationException ex,
            HttpServletRequest request
    ) {
        log.warn(
                "Database constraint violation for {}",
                route(request)
        );

        return buildResponse(
                HttpStatus.CONFLICT,
                "Operation conflicts with database constraints",
                request, "DATA_CONFLICT");
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handleMissingEndpoint(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Endpoint not found",
                request, "ENDPOINT_NOT_FOUND");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse body = buildResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint", request, "METHOD_NOT_ALLOWED").getBody();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(ex.getHeaders()).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(
            RateLimitExceededException ex,
            HttpServletRequest request
    ) {
        log.warn("event=request_rate_limited route={}", route(request));
        ResponseEntity<ApiErrorResponse> result = buildResponse(
                HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, "RATE_LIMIT_EXCEEDED");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.RETRY_AFTER,
                        Long.toString(ex.getRetryAfterSeconds()))
                .body(result.getBody());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (status.is5xxServerError()) {
            log.error("event=service_error status={} route={}", status.value(), route(request));
        }
        return buildResponse(status, status.getReasonPhrase(), request,
                status == HttpStatus.SERVICE_UNAVAILABLE ? "SERVICE_UNAVAILABLE" : "HTTP_" + status.value());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported request content type", request, "UNSUPPORTED_MEDIA_TYPE");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_ACCEPTABLE,
                "Requested response format is not supported", request, "NOT_ACCEPTABLE");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error(
                "event=unexpected_error route={} type={} stack={}",
                route(request), ex.getClass().getName(),
                Arrays.toString(Arrays.stream(ex.getStackTrace()).limit(32).toArray())
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                request, "INTERNAL_ERROR");
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            String code
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                code,
                RequestIdFilter.requestId(request)
        );

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
