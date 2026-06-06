package com.expensive.Expensive.Tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
public class ResponseDTO {
    private String status;
    private HttpStatus statusCode;
    private String message;
}
