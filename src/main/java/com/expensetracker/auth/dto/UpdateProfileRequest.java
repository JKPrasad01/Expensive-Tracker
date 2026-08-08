package com.expensetracker.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UpdateProfileRequest {

    @Pattern(
            regexp = "^[A-Za-z][A-Za-z ]*$",
            message = "Full name must start with a letter and contain only letters and spaces"
    )
    private String fullName;

    @Pattern(
            regexp = "^[0-9]{10,13}$",
            message = "Phone number must contain only digits and be 10 to 13 digits long"
    )
    private String phone;

    private String currentPassword;

    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,64}$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit and one special character"
    )
    private String newPassword;

    @AssertTrue(message = "Current password is required to set a new password")
    public boolean isPasswordChangeValid() {
        return newPassword == null
                || (currentPassword != null && !currentPassword.isBlank());
    }
}
