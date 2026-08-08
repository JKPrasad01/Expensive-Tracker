package com.expensetracker.service.impl;

import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.resource.ResourceCreateRequest;
import com.expensetracker.dto.resource.ResourceCreateResponse;
import com.expensetracker.dto.resource.ResourceHierarchyResponse;
import com.expensetracker.entity.Resource;
import com.expensetracker.exception.ResourceNameAlreadyExistsException;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.mapper.ResourceMapper;
import com.expensetracker.repository.ResourceRepository;
import com.expensetracker.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceMapper resourceMapper;

    @Override
    @Transactional
    public ApiResponse<ResourceCreateResponse> createResource(ResourceCreateRequest request) {

        validateResourceName(request.getResourceName());

        Resource parent = null;

        if (request.getParentId() != null) {
            parent = resourceRepository.findById(request.getParentId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Parent resource not found"));
        }

        String resourceKey = generateResourceKey(request.getResourceName(), parent);

        if (resourceRepository.existsByResourceKeyEqualsIgnoreCase(resourceKey)) {
            throw new ResourceNameAlreadyExistsException("Resource key already exists");
        }

        Resource resource = mapToResource(request, parent, resourceKey);

        Resource savedResource;
        try {
            savedResource = resourceRepository.save(resource);
        } catch (DataIntegrityViolationException e) {
            // Backstop for a race where two concurrent requests both pass
            // the exists() check above before either commits.
            throw new ResourceNameAlreadyExistsException("Resource key already exists");
        }

        return ApiResponse.<ResourceCreateResponse>builder()
                .data(resourceMapper.toCreateResponse(savedResource))
                .status(HttpStatus.CREATED)
                .message("success")
                .build();
    }

    private Resource mapToResource(ResourceCreateRequest request, Resource parent, String resourceKey) {
        Resource resource = new Resource();
        resource.setResourceName(request.getResourceName().trim());
        resource.setDisplayOrder(request.getDisplayOrder());
        resource.setPath(request.getPath());
        resource.setResourceType(request.getResourceType());
        resource.initiateKey(resourceKey);
        resource.setParent(parent);
        return resource;
    }


    @Override
    @Transactional
    public ApiResponse<List<ResourceCreateResponse>> createBulkResources(
            List<ResourceCreateRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            return ApiResponse.<List<ResourceCreateResponse>>builder()
                    .data(Collections.emptyList())
                    .status(HttpStatus.NO_CONTENT)
                    .message("No resources to process")
                    .build();
        }

        requests.forEach(r -> validateResourceName(r.getResourceName()));

        // Load parents
        Set<Long> parentIds = requests.stream()
                .map(ResourceCreateRequest::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Resource> parentMap = resourceRepository
                .findAllById(parentIds)
                .stream()
                .collect(Collectors.toMap(Resource::getId, Function.identity()));

        Set<Long> missingParentIds = parentIds.stream()
                .filter(id -> !parentMap.containsKey(id))
                .collect(Collectors.toSet());

        if (!missingParentIds.isEmpty()) {
            throw new ResourceNotFoundException("Parent resources not found: " + missingParentIds);
        }

        // Generate keys for every request (index-based, not identity-based,
        // so duplicate-looking requests in the input list are never silently dropped)
        List<String> resourceKeys = requests.stream()
                .map(request -> {
                    Resource parent = request.getParentId() == null
                            ? null
                            : parentMap.get(request.getParentId());
                    return generateResourceKey(request.getResourceName(), parent);
                })
                .toList();

        // Reject duplicate keys within the same batch (e.g. two siblings that
        // resolve to the same generated key) before touching the DB
        Set<String> seen = new HashSet<>();
        Set<String> duplicatesInBatch = resourceKeys.stream()
                .filter(k -> !seen.add(k))
                .collect(Collectors.toSet());

        if (!duplicatesInBatch.isEmpty()) {
            throw new ResourceNameAlreadyExistsException(
                    "Duplicate resource keys within request batch: " + duplicatesInBatch);
        }

        // Reject any key that already exists in the DB — bulk create never upserts
        Set<String> alreadyExisting = resourceRepository.findByResourceKeyIn(new HashSet<>(resourceKeys))
                .stream()
                .map(Resource::getResourceKey)
                .collect(Collectors.toSet());

        if (!alreadyExisting.isEmpty()) {
            throw new ResourceNameAlreadyExistsException(
                    "Resource keys already exist: " + alreadyExisting);
        }

        List<Resource> resourcesToSave = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {

            ResourceCreateRequest request = requests.get(i);
            String resourceKey = resourceKeys.get(i);

            Resource parent = request.getParentId() == null
                    ? null
                    : parentMap.get(request.getParentId());

            Resource resource = mapToResource(request, parent, resourceKey);

            resourcesToSave.add(resource);
        }

        List<ResourceCreateResponse> responses;
        try {
            responses = resourceRepository.saveAll(resourcesToSave)
                    .stream()
                    .map(resourceMapper::toCreateResponse)
                    .toList();
        } catch (DataIntegrityViolationException e) {
            // Backstop for a race between the existence check above and this save.
            throw new ResourceNameAlreadyExistsException(
                    "One or more resource keys already exist (concurrent creation)");
        }

        return ApiResponse.<List<ResourceCreateResponse>>builder()
                .data(responses)
                .status(HttpStatus.CREATED)
                .message("Resources created successfully")
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<ResourceHierarchyResponse>> getResourceHierarchy() {

        List<Resource> resources = resourceRepository.findAllWithParent();

        Map<Long, ResourceHierarchyResponse> resourceMap = new HashMap<>();

        for (Resource resource : resources) {
            ResourceHierarchyResponse dto = new ResourceHierarchyResponse();
            dto.setId(resource.getId());
            dto.setResourceName(resource.getResourceName());
            dto.setResourceKey(resource.getResourceKey());
            dto.setPath(resource.getPath());
            dto.setDisplayOrder(resource.getDisplayOrder());
            dto.setResourceType(resource.getResourceType());
            resourceMap.put(resource.getId(), dto);
        }

        List<ResourceHierarchyResponse> roots = new ArrayList<>();

        for (Resource resource : resources) {

            ResourceHierarchyResponse current = resourceMap.get(resource.getId());

            if (resource.getParent() == null) {
                roots.add(current);
                continue;
            }

            ResourceHierarchyResponse parent = resourceMap.get(resource.getParent().getId());
            if (parent != null) {
                parent.getChildren().add(current);
            }
        }

        sortChildren(roots);

        return ApiResponse.<List<ResourceHierarchyResponse>>builder()
                .data(roots)
                .status(HttpStatus.OK)
                .message("success")
                .build();
    }


    private void validateResourceName(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("Resource name is required");
        }
    }

    private String generateResourceKey(String resourceName, Resource parent) {

        String currentKey = resourceName
                .trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");

        if (parent == null) {
            return currentKey;
        }

        return parent.getResourceKey() + "_" + currentKey;
    }


    private void sortChildren(List<ResourceHierarchyResponse> resources) {

        resources.sort(Comparator.comparing(
                ResourceHierarchyResponse::getDisplayOrder,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        for (ResourceHierarchyResponse resource : resources) {
            sortChildren(resource.getChildren());
        }
    }
}