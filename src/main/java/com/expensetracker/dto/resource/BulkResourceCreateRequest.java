package com.expensetracker.dto.resource;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkResourceCreateRequest {

    @NotEmpty(message = "At least one resource is required")
    @Valid
    private List<ResourceCreateRequest> resources;
}