package com.expensive.Expensive.Tracker.dto;

import lombok.Data;

@Data
public class CreateActionResponse {
    private Long id;
    private String actionName;
    private String actionKey;
    private String description;
    private boolean isActive;
}