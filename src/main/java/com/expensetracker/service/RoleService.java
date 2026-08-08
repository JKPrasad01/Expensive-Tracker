package com.expensetracker.service;

import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.RoleRequestDTO;
import com.expensetracker.dto.RoleResponseDTO;

public interface RoleService {

    ApiResponse<RoleResponseDTO> createRole(RoleRequestDTO roleRequestDTO);
    ApiResponse<RoleResponseDTO> getRole(Integer roleId);
}
