package com.expensive.Expensive.Tracker.exception;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;


@Builder
@Data
public class ErrorResponse {

    private String message;
    private int statusCode;
    private LocalDateTime localDateTime;
    private String path;
}
