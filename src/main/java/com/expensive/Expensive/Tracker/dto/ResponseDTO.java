package com.expensive.Expensive.Tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class ResponseDTO<T> {

    private HttpStatus status;
    private String message;

    @Builder.Default
    private LocalDateTime localDateTime=LocalDateTime.now();
    private T data;
}
