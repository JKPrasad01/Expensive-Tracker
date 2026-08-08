package com.expensetracker.controller;


import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.RoleRequestDTO;
import com.expensetracker.dto.RoleResponseDTO;
import com.expensetracker.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@Validated
public class RoleController {
    private final RoleService roleService;


    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponseDTO>> createRole(
            @RequestBody @Valid RoleRequestDTO roleRequestDTO){
        ApiResponse<RoleResponseDTO> response =roleService.createRole(roleRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoleResponseDTO>> getRoleById(
            @PathVariable
            @Positive(message = "role id must be greater than 0")
            Integer roleId) {

        ApiResponse<RoleResponseDTO> response = roleService.getRole(roleId);

        return ResponseEntity.ok(response);
    }
}
