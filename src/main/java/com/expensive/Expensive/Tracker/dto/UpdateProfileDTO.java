package com.expensive.Expensive.Tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateProfileDTO {

    @NotBlank(message = "Email is required")
    @Pattern(
            regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$",
            message = "Invalid email format"
    )
    private String email;


    @NotBlank(message = "password is required.")
    @Size(min = 8, max = 16,message = "password length should be between 8 to 16")
    private String password;


    @NotBlank(message = "Full name is required")
    @Size(min = 5, max = 20, message = "Full name must be between 5 and 20 characters")
    @Pattern(
            regexp = "^[A-Za-z][A-Za-z ]*$",
            message = "Full name must start with a letter and contain only letters and spaces"
    )
    private String fullName;
}
