package com.expensive.Expensive.Tracker.service;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.RoleRequestDTO;
import com.expensive.Expensive.Tracker.dto.RoleResponseDTO;

public interface RoleService {

    ResponseDTO<RoleResponseDTO> createRole(RoleRequestDTO roleRequestDTO);
    ResponseDTO<RoleResponseDTO> getRole(Integer roleId);
}
