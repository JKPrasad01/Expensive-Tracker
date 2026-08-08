package com.expensetracker.service.impl;

import com.expensetracker.dto.ApiResponse;
import com.expensetracker.dto.RoleRequestDTO;
import com.expensetracker.dto.RoleResponseDTO;
import com.expensetracker.entity.Role;
import com.expensetracker.exception.RoleAlreadyExistsException;
import com.expensetracker.exception.RoleNotFoundException;
import com.expensetracker.mapper.RoleMapper;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;



    @Override
    @Transactional
    public ApiResponse<RoleResponseDTO> createRole(RoleRequestDTO roleRequestDTO) {

        String normalizedRoleKey =
                getNormalizedRoleKey(roleRequestDTO.getRoleKey());

        if (roleRepository.existsByRoleKey(normalizedRoleKey)) {
            throw new RoleAlreadyExistsException(
                    "Role already exists with key: " + normalizedRoleKey);
        }

        Role role = roleMapper.requestDtoToRole(roleRequestDTO);
        role.setRoleKey(normalizedRoleKey);

        Role savedRole = roleRepository.save(role);

        return ApiResponse.<RoleResponseDTO>builder()
                .status(HttpStatus.CREATED)
                .message("Role created successfully")
                .data(roleMapper.roleToRoleResponseDto(savedRole))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<RoleResponseDTO> getRole(Integer roleId) {
        Role role = findOrThrow(roleId);

        return ApiResponse.<RoleResponseDTO>builder()
                .status(HttpStatus.OK)
                .message("Role fetched successfully")
                .data(roleMapper.roleToRoleResponseDto(role))
                .build();
    }

    private Role findOrThrow(Integer roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new RoleNotFoundException(
                                "Role not found with id: " + roleId));
    }

    private String getNormalizedRoleKey(String roleKey) {
        return roleKey == null ? null : roleKey.trim().toUpperCase();
    }
}
