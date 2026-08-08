package com.expensetracker.service;

import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.resource.ResourceCreateRequest;
import com.expensetracker.dto.resource.ResourceCreateResponse;
import com.expensetracker.dto.resource.ResourceHierarchyResponse;

import java.util.List;

public interface ResourceService {

    ApiResponse<ResourceCreateResponse> createResource(
            ResourceCreateRequest request);

    ApiResponse<List<ResourceCreateResponse>> createBulkResources(
            List<ResourceCreateRequest> requests);

    ApiResponse<List<ResourceHierarchyResponse>> getResourceHierarchy();
}
