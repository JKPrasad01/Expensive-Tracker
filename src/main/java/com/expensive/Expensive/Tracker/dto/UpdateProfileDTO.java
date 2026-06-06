package com.expensive.Expensive.Tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateProfileDTO {
    private String email;
    private String password;
    private String fullName;
}
