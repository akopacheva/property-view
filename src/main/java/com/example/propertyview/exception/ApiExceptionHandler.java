package com.example.propertyview.exception;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles exceptions across REST controllers using RFC 9457 Problem Details.
 * Inherits handling for standard Spring MVC errors, including HTTP 400, 405 and 415.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Returns HTTP 404 when the requested hotel cannot be found.
     * Uses the exception message as the problem detail.
     */
    @ExceptionHandler(HotelNotFoundException.class)
    public ProblemDetail handleNotFound(HotelNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * Returns HTTP 400 when an operation receives an invalid argument.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * Customizes the response for request body validation failures.
     * Adds field-specific validation messages to the Problem Details response.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        // Create a problem response using the HTTP status supplied by Spring.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status, "Request validation failed");

        // Preserve field order and keep only the first validation message per field.
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(), error.getDefaultMessage()));

        // Expose validation errors as an additional property in the response body.
        problem.setProperty("errors", errors);

        // Delegate response construction to the Spring MVC exception handler.
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}