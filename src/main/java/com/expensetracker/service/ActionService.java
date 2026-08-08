package com.expensetracker.service;

import com.expensetracker.dto.CreateActionRequest;
import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.CreateActionResponse;


public interface ActionService {

    ApiResponse<CreateActionResponse> createAction(CreateActionRequest createAction);
}