package com.expensive.Expensive.Tracker.service;

import com.expensive.Expensive.Tracker.dto.CreateActionRequest;
import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.CreateActionResponse;


public interface ActionService {

    ResponseDTO<CreateActionResponse> createAction(CreateActionRequest createAction);
}