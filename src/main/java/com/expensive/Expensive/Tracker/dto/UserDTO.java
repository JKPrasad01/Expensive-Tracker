package com.expensive.Expensive.Tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {
    private String fullName;
    private String email;
    private String password;
    private String phone;
    private long role;
}