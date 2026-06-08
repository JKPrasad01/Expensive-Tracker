package com.expensive.Expensive.Tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequestDTO {

    @NotBlank(message = "Role key is required")
    @Size(
            min = 3,
            max = 20,
            message = "Role key must be between 3 and 20 characters"
    )
    private String roleKey;

    private String description;
}
