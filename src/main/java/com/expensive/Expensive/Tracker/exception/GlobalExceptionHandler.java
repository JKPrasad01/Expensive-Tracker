package com.expensive.Expensive.Tracker.exception;


import jakarta.servlet.http.HttpServletRequest;
import org.apache.coyote.Response;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRoleNotFoundException(
            RoleNotFoundException ex,
            HttpServletRequest request){

        ErrorResponse errorResponse = ErrorResponse.builder()
                .message(ex.getMessage())
                .statusCode(HttpStatus.NOT_FOUND.value())
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(RoleAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleRoleAlreadyExistsException(
            RoleAlreadyExistsException ex,
            HttpServletRequest httpServletRequest){

        ErrorResponse errorResponse=ErrorResponse.builder()
                .message(ex.getMessage())
                .statusCode(HttpStatus.CONFLICT.value())
                .path(httpServletRequest.getRequestURI())
                .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex,
            HttpServletRequest httpServletRequest
    ){
        ErrorResponse errorResponse=ErrorResponse.builder()
                .message(ex.getMessage())
                .statusCode(HttpStatus.CONFLICT.value())
                .path(httpServletRequest.getRequestURI())
                .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
}
