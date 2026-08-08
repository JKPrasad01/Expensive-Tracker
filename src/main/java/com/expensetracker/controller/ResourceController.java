package com.expensetracker.controller;

import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.resource.BulkResourceCreateRequest;
import com.expensetracker.dto.resource.ResourceCreateRequest;
import com.expensetracker.dto.resource.ResourceCreateResponse;
import com.expensetracker.dto.resource.ResourceHierarchyResponse;
import com.expensetracker.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ResourceCreateResponse>> createResource(
            @Valid @RequestBody ResourceCreateRequest request) {

        ApiResponse<ResourceCreateResponse> response =
                resourceService.createResource(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<ResourceCreateResponse>>> createBulkResources(
            @Valid @RequestBody BulkResourceCreateRequest requests) {

        ApiResponse<List<ResourceCreateResponse>> response =
                resourceService.createBulkResources(requests.getResources());

        return ResponseEntity.status(response.getStatus()).body(response);
    }

    @GetMapping("/hierarchy")
    public ResponseEntity<ApiResponse<List<ResourceHierarchyResponse>>> getResourceHierarchy() {

        ApiResponse<List<ResourceHierarchyResponse>> response =
                resourceService.getResourceHierarchy();

        return ResponseEntity.status(response.getStatus()).body(response);
    }
}