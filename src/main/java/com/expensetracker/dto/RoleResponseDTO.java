package com.expensetracker.dto;

import lombok.Data;

@Data
public class RoleResponseDTO {
    private Integer id;
    private String roleKey;
    private String description;
}
