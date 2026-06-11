package com.expensive.Expensive.Tracker.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFoundException(
            RoleNotFoundException ex,
            HttpServletRequest request) {

        return buildErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(RoleAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleRoleAlreadyExistsException(
            RoleAlreadyExistsException ex,
            HttpServletRequest request) {

        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ActionAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleActionAlreadyExistsException(
            ActionAlreadyExistsException ex,
            HttpServletRequest request) {

        return buildErrorResponse(ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            String message,
            HttpStatus status,
            HttpServletRequest request) {

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(message)
                .statusCode(status.value())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }
}