package com.expensetracker.dto.resource;

import com.expensetracker.enums.ResourceType;
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