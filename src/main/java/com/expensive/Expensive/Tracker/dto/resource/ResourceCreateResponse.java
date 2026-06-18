package com.expensive.Expensive.Tracker.dto.resource;


import com.expensive.Expensive.Tracker.enums.ResourceType;
import lombok.Data;

@Data
public class ResourceCreateResponse {
    private String resourceName;
    private String resourceKey;
    private Long parentId;
    private ResourceType resourceType;
    private String path;
}
