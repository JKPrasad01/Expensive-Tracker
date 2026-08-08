package com.expensetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private HttpStatus status;
    private String message;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    private T data;
}