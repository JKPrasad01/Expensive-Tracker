package com.expensetracker.mapper;

import com.expensetracker.dto.CreateActionRequest;
import com.expensetracker.dto.CreateActionResponse;
import com.expensetracker.entity.Action;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ActionMapper {

    CreateActionResponse entityToCreateActionResponse(Action action);

    Action createActionRequestToEntity(CreateActionRequest createActionRequest);
}