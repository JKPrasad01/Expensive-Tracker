package com.expensetracker.controller;

import com.expensetracker.dto.CreateActionRequest;
import com.expensetracker.dto.CreateActionResponse;
import com.expensetracker.dto.ApiResponse;
import com.expensetracker.service.ActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CreateActionResponse>> createAction(
            @RequestBody @Valid CreateActionRequest createActionRequest) {

        ApiResponse<CreateActionResponse> response =
            actionService.createAction(createActionRequest);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(response);
    }
}