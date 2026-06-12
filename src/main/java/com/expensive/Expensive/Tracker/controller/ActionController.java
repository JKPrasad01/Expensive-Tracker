package com.expensive.Expensive.Tracker.controller;

import com.expensive.Expensive.Tracker.dto.CreateActionRequest;
import com.expensive.Expensive.Tracker.dto.CreateActionResponse;
import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.service.ActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    @PostMapping
    public ResponseEntity<ResponseDTO<CreateActionResponse>> createAction(
            @RequestBody @Valid CreateActionRequest createActionRequest) {

        ResponseDTO<CreateActionResponse> response =
            actionService.createAction(createActionRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }
}