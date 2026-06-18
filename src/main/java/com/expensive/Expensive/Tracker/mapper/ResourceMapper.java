package com.expensive.Expensive.Tracker.mapper;


import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateRequest;
import com.expensive.Expensive.Tracker.dto.resource.ResourceCreateResponse;
import com.expensive.Expensive.Tracker.entity.Resource;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ResourceMapper {


    Resource createRequestToEntity(ResourceCreateRequest request);

    ResourceCreateResponse toCreateResponse(Resource resource);

}
