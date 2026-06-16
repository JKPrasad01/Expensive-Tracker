package com.expensive.Expensive.Tracker.service.impl;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateRequest;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateResponse;
import com.expensive.Expensive.Tracker.dto.resource.ResourceHierarchyResponse;
import com.expensive.Expensive.Tracker.entity.Resource;
import com.expensive.Expensive.Tracker.exception.ResourceNameAlreadyExistsException;
import com.expensive.Expensive.Tracker.exception.ResourceNotFoundException;
import com.expensive.Expensive.Tracker.mapper.ResourceMapper;
import com.expensive.Expensive.Tracker.repository.ResourceRepository;
import com.expensive.Expensive.Tracker.service.ResourceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {
    private final ResourceRepository resourceRepository;
    private final ResourceMapper resourceMapper;

    @Override
    public ResponseDTO<ResourceCreateResponse> createResource(ResourceCreateRequest request) {

        Resource parent = null;

        if (request.getParentId() != null) {
            parent = resourceRepository.findById(request.getParentId())
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Parent resource not found"));
        }

        String resourceKey = generateResourceKey(
                request.getResourceName(),
                parent
        );

        if (resourceRepository.existsByResourceKeyEqualsIgnoreCase(resourceKey)) {
            throw new ResourceNameAlreadyExistsException(
                    "Resource key already exists");
        }

        Resource resource = new Resource();

        resource.setResourceName(request.getResourceName());
        resource.setDisplayOrder(request.getDisplayOrder());
        resource.setPath(request.getPath());
        resource.setResourceType(request.getResourceType());

        resource.initiateKey(resourceKey);

        resource.setParent(parent);

        Resource savedResource = resourceRepository.save(resource);

        return ResponseDTO.<ResourceCreateResponse>builder()
                .data(resourceMapper.toCreateResponse(savedResource))
                .status(HttpStatus.CREATED)
                .message("success")
                .build();
    }


    @Transactional
    @Override
    public ResponseDTO<List<ResourceCreateResponse>> createBulkResources(
            Set<ResourceCreateRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            return ResponseDTO.<List<ResourceCreateResponse>>builder()
                    .data(Collections.emptyList())
                    .status(HttpStatus.NO_CONTENT)
                    .message("No resources to process")
                    .build();
        }

        // Load parents
        Set<Long> parentIds = requests.stream()
                .map(ResourceCreateRequest::getParentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Resource> parentMap = resourceRepository
                .findAllById(parentIds)
                .stream()
                .collect(Collectors.toMap(
                        Resource::getId,
                        Function.identity()
                ));

        // Validate parent ids
        Set<Long> missingParentIds = parentIds.stream()
                .filter(id -> !parentMap.containsKey(id))
                .collect(Collectors.toSet());

        if (!missingParentIds.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Parent resources not found: " + missingParentIds
            );
        }

        // Generate all resource keys
        Map<ResourceCreateRequest, String> requestKeyMap =
                requests.stream()
                        .collect(Collectors.toMap(
                                Function.identity(),
                                request -> {

                                    Resource parent =
                                            request.getParentId() == null
                                                    ? null
                                                    : parentMap.get(request.getParentId());

                                    return generateResourceKey(
                                            request.getResourceName(),
                                            parent
                                    );
                                }
                        ));

        Set<String> resourceKeys =
                new HashSet<>(requestKeyMap.values());

        // Load existing resources in single query
        Map<String, Resource> existingResources =
                resourceRepository.findByResourceKeyIn(resourceKeys)
                        .stream()
                        .collect(Collectors.toMap(
                                Resource::getResourceKey,
                                Function.identity()
                        ));

        List<Resource> resourcesToSave = new ArrayList<>();

        for (ResourceCreateRequest request : requests) {

            String resourceKey = requestKeyMap.get(request);

            Resource resource =
                    existingResources.get(resourceKey);

            if (resource == null) {
                resource = new Resource();
                resource.initiateKey(resourceKey);
            }

            Resource parent =
                    request.getParentId() == null
                            ? null
                            : parentMap.get(request.getParentId());

            resource.setResourceName(request.getResourceName());
            resource.setDisplayOrder(request.getDisplayOrder());
            resource.setPath(request.getPath());
            resource.setResourceType(request.getResourceType());
            resource.setParent(parent);

            resourcesToSave.add(resource);
        }

        List<ResourceCreateResponse> responses =
                resourceRepository.saveAll(resourcesToSave)
                        .stream()
                        .map(resourceMapper::toCreateResponse)
                        .toList();

        return ResponseDTO.<List<ResourceCreateResponse>>builder()
                .data(responses)
                .status(HttpStatus.CREATED)
                .message("Resources processed successfully")
                .build();
    }


    @Override
    public ResponseDTO<List<ResourceHierarchyResponse>> getResourceHierarchy() {

        List<Resource> resources = resourceRepository.findAll();

        Map<Long, ResourceHierarchyResponse> resourceMap = new HashMap<>();

        // First pass: create DTOs
        for (Resource resource : resources) {

            ResourceHierarchyResponse dto =
                    new ResourceHierarchyResponse();

            dto.setId(resource.getId());
            dto.setResourceName(resource.getResourceName());
            dto.setResourceKey(resource.getResourceKey());
            dto.setPath(resource.getPath());
            dto.setDisplayOrder(resource.getDisplayOrder());
            dto.setResourceType(resource.getResourceType());

            resourceMap.put(resource.getId(), dto);
        }

        List<ResourceHierarchyResponse> roots = new ArrayList<>();

        // Second pass: build hierarchy
        for (Resource resource : resources) {

            ResourceHierarchyResponse current =
                    resourceMap.get(resource.getId());

            if (resource.getParent() == null) {
                roots.add(current);
                continue;
            }

            ResourceHierarchyResponse parent =
                    resourceMap.get(resource.getParent().getId());

            if (parent != null) {
                parent.getChildren().add(current);
            }
        }

        // Sort recursively
        sortChildren(roots);

        return ResponseDTO.<List<ResourceHierarchyResponse>>builder()
                .data(roots)
                .status(HttpStatus.OK)
                .message("success")
                .build();
    }


    private String generateResourceKey(
            String resourceName,
            Resource parent) {

        String currentKey = resourceName.trim()
                .toUpperCase()
                .replace(" ", "_");

        if (parent == null) {
            return currentKey;
        }

        return parent.getResourceKey() + "_" + currentKey;
    }


    private void sortChildren(
            List<ResourceHierarchyResponse> resources) {

        resources.sort(
                Comparator.comparing(
                        ResourceHierarchyResponse::getDisplayOrder
                )
        );

        for (ResourceHierarchyResponse resource : resources) {
            sortChildren(resource.getChildren());
        }
    }

}
