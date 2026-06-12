package com.expensive.Expensive.Tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateActionRequest {

    @NotBlank(message = "Action name is required")
    @Size(min = 3, max = 20, message = "Action name must be between 3 and 20 characters")
    private String actionName;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;
}