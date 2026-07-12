package com.expensive.Expensive.Tracker.controller;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.resource.BulkResourceCreateRequest;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateRequest;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateResponse;
import com.expensive.Expensive.Tracker.dto.resource.ResourceHierarchyResponse;
import com.expensive.Expensive.Tracker.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    public ResponseEntity<ResponseDTO<ResourceCreateResponse>> createResource(
            @Valid @RequestBody ResourceCreateRequest request) {

        ResponseDTO<ResourceCreateResponse> response =
                resourceService.createResource(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk")
    public ResponseEntity<ResponseDTO<List<ResourceCreateResponse>>> createBulkResources(
            @Valid @RequestBody BulkResourceCreateRequest requests) {

        ResponseDTO<List<ResourceCreateResponse>> response =
                resourceService.createBulkResources(requests.getResources());

        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<ResponseDTO<List<ResourceHierarchyResponse>>> getResourceHierarchy() {

        ResponseDTO<List<ResourceHierarchyResponse>> response =
                resourceService.getResourceHierarchy();

        return ResponseEntity.status(response.getStatus()).body(response);
    }
}