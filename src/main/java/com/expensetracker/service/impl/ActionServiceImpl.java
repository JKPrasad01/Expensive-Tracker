package com.expensetracker.service.impl;

import com.expensetracker.dto.CreateActionRequest;
import com.expensetracker.dto.CreateActionResponse;
import com.expensetracker.dto.ApiResponse;
import com.expensetracker.entity.Action;
import com.expensetracker.exception.ActionAlreadyExistsException;
import com.expensetracker.mapper.ActionMapper;
import com.expensetracker.repository.ActionRepository;
import com.expensetracker.service.ActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActionServiceImpl implements ActionService {
    private final ActionMapper actionMapper;
    private final ActionRepository actionRepository;

    @Override
    @Transactional
    public ApiResponse<CreateActionResponse> createAction(CreateActionRequest request) {

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

        Action savedAction;
        try {
            savedAction = actionRepository.save(action);
        } catch (DataIntegrityViolationException e) {
            // Backstop for a race where two concurrent requests both pass
            // the exists() checks above before either commits.
            throw new ActionAlreadyExistsException(
                    "Action already exists with name or key: " + actionName);
        }

        CreateActionResponse response =
                actionMapper.entityToCreateActionResponse(savedAction);

        return ApiResponse.<CreateActionResponse>builder()
                .status(HttpStatus.CREATED)
                .message("Action created successfully")
                .data(response)
                .build();
    }

    private String generateActionKey(String actionName) {
        return actionName
                .trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9\\s]", "")
                .trim()
                .replaceAll("\\s+", "_");
    }
}