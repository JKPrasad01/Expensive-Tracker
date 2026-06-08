package com.expensive.Expensive.Tracker.exception;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;


@Builder
@Data
public class ErrorResponse {

    private String message;
    private int statusCode;
    @Builder.Default
    private LocalDateTime localDateTime=LocalDateTime.now();
    private String path;
}
