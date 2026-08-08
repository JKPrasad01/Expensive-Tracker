package com.expensetracker.dto.resource;


import com.expensetracker.enums.ResourceType;
import lombok.Data;

@Data
public class ResourceCreateResponse {
    private String resourceName;
    private String resourceKey;
    private Long parentId;
    private ResourceType resourceType;
    private String path;
}
