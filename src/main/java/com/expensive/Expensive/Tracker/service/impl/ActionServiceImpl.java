package com.expensive.Expensive.Tracker.service.impl;

import com.expensive.Expensive.Tracker.dto.CreateActionRequest;
import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.entity.Action;
import com.expensive.Expensive.Tracker.repository.ActionRepository;
import com.expensive.Expensive.Tracker.service.ActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.expensive.Expensive.Tracker.dto.CreateActionResponse;
import com.expensive.Expensive.Tracker.mapper.ActionMapper;
import com.expensive.Expensive.Tracker.exception.ActionAlreadyExistsException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActionServiceImpl implements ActionService {
    private final ActionMapper actionMapper;
    private final ActionRepository actionRepository;

    @Override
    @Transactional
    public ResponseDTO<CreateActionResponse> createAction(CreateActionRequest request) {

        String actionName = request.getActionName().trim();

        if (actionRepository.existsByActionNameIgnoreCase(actionName)) {
            throw new ActionAlreadyExistsException(
                    "Action already exists with name: " + actionName);
        }

        String actionKey = generateActionKey(actionName);

        if (actionRepository.existsByActionKeyIgnoreCase(actionKey)) {
            throw new ActionAlreadyExistsException(
                "Action already exists with key: " + actionKey);
        }

        Action action = actionMapper.createActionRequestToEntity(request);
        action.setActionName(actionName);
        action.initializeKey(actionKey);

        Action savedAction = actionRepository.save(action);

        CreateActionResponse response =
            actionMapper.entityToCreateActionResponse(savedAction);

        return ResponseDTO.<CreateActionResponse>builder()
            .status(HttpStatus.CREATED)
            .message("Action created successfully")
            .data(response)
            .build();
    }

    
    private String generateActionKey(String actionName) {
        return actionName
                .trim()
                .toUpperCase()
                .replace(" ", "_");
    }
}