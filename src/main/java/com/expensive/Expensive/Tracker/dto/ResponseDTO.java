package com.expensive.Expensive.Tracker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseDTO<T> {

    private HttpStatus status;
    private String message;

    @Builder.Default
    private LocalDateTime timeStamps = LocalDateTime.now();
    private T data;
}