package com.expensive.Expensive.Tracker.mapper;

import com.expensive.Expensive.Tracker.dto.CreateActionRequest;
import com.expensive.Expensive.Tracker.dto.CreateActionResponse;
import com.expensive.Expensive.Tracker.entity.Action;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ActionMapper {

    CreateActionResponse entityToCreateActionResponse(Action action);

    Action createActionRequestToEntity(CreateActionRequest createActionRequest);
}