package com.expensetracker.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
@Data
public class ErrorResponse {

    private String message;
    private int statusCode;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private String path;
    private Map<String, String> fieldErrors;
}
