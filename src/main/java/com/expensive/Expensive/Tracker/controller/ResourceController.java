package com.expensive.Expensive.Tracker.controller;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateRequest;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateResponse;
import com.expensive.Expensive.Tracker.dto.resource.ResourceHierarchyResponse;
import com.expensive.Expensive.Tracker.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    public ResponseDTO<ResourceCreateResponse> createResource(
            @Valid @RequestBody ResourceCreateRequest request) {

        return (ResponseDTO<ResourceCreateResponse>)
                resourceService.createResource(request);
    }

    @PostMapping("/bulk")
    public ResponseDTO<List<ResourceCreateResponse>> createBulkResources(
            @Valid @RequestBody Set<ResourceCreateRequest> requests) {

        return (ResponseDTO<List<ResourceCreateResponse>>)
                resourceService.createBulkResources(requests);
    }

    @GetMapping("/hierarchy")
    public ResponseDTO<List<ResourceHierarchyResponse>> getResourceHierarchy() {
        return resourceService.getResourceHierarchy();
    }
}