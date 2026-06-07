package com.expensive.Expensive.Tracker.controller;


import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.RoleRequestDTO;
import com.expensive.Expensive.Tracker.dto.RoleResponseDTO;
import com.expensive.Expensive.Tracker.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/role")
@RequiredArgsConstructor
@Validated
public class RoleController {
    private final RoleService roleService;


    @PostMapping("/create")
    public ResponseEntity<ResponseDTO<RoleResponseDTO>> createRole(
            @RequestBody @Valid RoleRequestDTO roleRequestDTO){
        ResponseDTO<RoleResponseDTO> response =roleService.createRole(roleRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<ResponseDTO<RoleResponseDTO>> getRoleById(
            @PathVariable
            @Positive(message = "role id must be greater than 0")
            Integer roleId) {

        ResponseDTO<RoleResponseDTO> response = roleService.getRole(roleId);

        return ResponseEntity.ok(response);
    }
}
