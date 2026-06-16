package com.expensive.Expensive.Tracker.service;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateRequest;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateResponse;
import com.expensive.Expensive.Tracker.dto.resource.ResourceHierarchyResponse;

import java.util.List;
import java.util.Set;

public interface ResourceService {

    ResponseDTO<ResourceCreateResponse> createResource(
            ResourceCreateRequest request);

    ResponseDTO<List<ResourceCreateResponse>> createBulkResources(
            Set<ResourceCreateRequest> requests);

    ResponseDTO<List<ResourceHierarchyResponse>> getResourceHierarchy();
}
