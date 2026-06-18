package com.expensive.Expensive.Tracker.dto.resource;

import com.expensive.Expensive.Tracker.enums.ResourceType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResourceHierarchyResponse {

    private Long id;
    private String resourceName;
    private String resourceKey;
    private String path;
    private Integer displayOrder;
    private ResourceType resourceType;

    private List<ResourceHierarchyResponse> children = new ArrayList<>();
}