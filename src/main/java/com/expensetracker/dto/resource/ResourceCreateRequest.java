package com.expensetracker.dto.resource;


import com.expensetracker.enums.ResourceType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
public class ResourceCreateRequest {

    @NotBlank(message = "resource name is required")
    private String resourceName;

    @NotNull(message = "display order is required")
    @Min(value = 1, message = "display order should be greater than zero")
    private Integer displayOrder;

    private String path;

    @NotNull(message = "resource type is required")
    private ResourceType resourceType;

    private Long parentId;
}