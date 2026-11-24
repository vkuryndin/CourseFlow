package org.courseflow.common.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.courseflow.testing.Fixtures.CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerBoundaryTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(CLOCK);
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/courses");

    private void check(ResponseEntity<ApiErrorResponse> result, int status, String code) {
        assertEquals(status, result.getStatusCode().value());
        assertEquals(code, result.getBody().code());
        assertEquals("/api/courses", result.getBody().path());
    }

    @Test
    void concurrencyAndConstraintErrorsAreConflictsWithoutDatabaseDetails() {
        var concurrency = handler.handleConcurrentConflict(new OptimisticLockException("internal"), request);
        check(concurrency, 409, "CONCURRENT_CONFLICT");
        var constraint = handler.handleDataConflict(new DataIntegrityViolationException("SQL details"), request);
        check(constraint, 409, "DATA_CONFLICT");
        assertFalse(constraint.getBody().message().contains("SQL details"));
    }

    @Test
    void rateLimitReturnsRetryAfter() {
        var result = handler.handleRateLimit(new RateLimitExceededException(42), request);
        check(result, 429, "RATE_LIMIT_EXCEEDED");
        assertEquals("42", result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void unavailableAndOtherHttpErrorsPreserveStatus() {
        check(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), request),
                503, "SERVICE_UNAVAILABLE");
        check(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_GATEWAY), request),
                502, "HTTP_502");
        check(handler.handleResponseStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST), request),
                400, "HTTP_400");
    }

    @Test
    void malformedAndUnsupportedRequestsHaveDistinctCodes() {
        check(handler.handleInvalidJson(mock(HttpMessageNotReadableException.class), request), 400, "INVALID_JSON");
        check(handler.handleParameterValidation(mock(HandlerMethodValidationException.class), request),
                400, "VALIDATION_FAILED");
        check(handler.handleUnsupportedMediaType(mock(HttpMediaTypeNotSupportedException.class), request),
                415, "UNSUPPORTED_MEDIA_TYPE");
        check(handler.handleNotAcceptable(mock(HttpMediaTypeNotAcceptableException.class), request),
                406, "NOT_ACCEPTABLE");
    }

    @Test
    void missingHeaderParameterAndWrongTypeIdentifyField() {
        var header = mock(MissingRequestHeaderException.class);
        when(header.getHeaderName()).thenReturn("Authorization");
        var parameter = mock(MissingServletRequestParameterException.class);
        when(parameter.getParameterName()).thenReturn("courseId");
        var mismatch = mock(MethodArgumentTypeMismatchException.class);
        when(mismatch.getName()).thenReturn("page");
        assertEquals("Missing header: Authorization", handler.handleMissingHeader(header, request).getBody().message());
        assertEquals("Missing parameter: courseId", handler.handleMissingParameter(parameter, request).getBody().message());
        assertEquals("Invalid value for parameter: page", handler.handleTypeMismatch(mismatch, request).getBody().message());
    }

    @Test
    void validationErrorsAreSortedDeduplicatedAndNeverEchoRejectedValue() {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "title", "required"));
        binding.addError(new FieldError("request", "email", "invalid"));
        binding.addError(new FieldError("request", "email", "invalid"));
        var exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(binding);
        var result = handler.handleValidation(exception, request);
        check(result, 400, "VALIDATION_FAILED");
        assertEquals("email: invalid; title: required", result.getBody().message());
        check(handler.handleConstraintViolation(new ConstraintViolationException("password", Set.of()), request),
                400, "VALIDATION_FAILED");
    }

    @Test
    void passwordByteLengthValidationUsesBadRequestCode() {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "password", "secret", false,
                new String[]{"MaxUtf8Bytes"}, null, "Too long"));
        var exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(binding);
        var result = handler.handleValidation(exception, request);
        check(result, 400, "BAD_REQUEST");
        assertEquals("Too long", result.getBody().message());
    }

    @Test
    void unknownEndpointAndUnexpectedErrorsHideInternalDetails() {
        check(handler.handleMissingEndpoint(new IllegalArgumentException("private"), request), 404, "ENDPOINT_NOT_FOUND");
        var result = handler.handleUnexpected(new IllegalStateException("private"), request);
        check(result, 500, "INTERNAL_ERROR");
        assertEquals("Internal server error", result.getBody().message());
    }

    @Test
    void methodNotAllowedPreservesAllowHeader() {
        var exception = mock(HttpRequestMethodNotSupportedException.class);
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.ALLOW, "GET");
        when(exception.getHeaders()).thenReturn(headers);
        var result = handler.handleMethodNotAllowed(exception, request);
        check(result, 405, "METHOD_NOT_ALLOWED");
        assertTrue(result.getHeaders().getFirst(HttpHeaders.ALLOW).contains("GET"));
    }
}
