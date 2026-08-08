package com.expensetracker.mapper;


import com.expensetracker.dto.resource.ResourceCreateRequest;
import com.expensetracker.dto.resource.ResourceCreateResponse;
import com.expensetracker.entity.Resource;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ResourceMapper {


    Resource createRequestToEntity(ResourceCreateRequest request);

    ResourceCreateResponse toCreateResponse(Resource resource);

}
