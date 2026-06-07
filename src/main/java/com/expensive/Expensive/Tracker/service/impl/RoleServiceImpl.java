package com.expensive.Expensive.Tracker.service.impl;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.RoleRequestDTO;
import com.expensive.Expensive.Tracker.dto.RoleResponseDTO;
import com.expensive.Expensive.Tracker.entity.Role;
import com.expensive.Expensive.Tracker.exception.RoleAlreadyExistsException;
import com.expensive.Expensive.Tracker.exception.RoleNotFoundException;
import com.expensive.Expensive.Tracker.mapper.RoleMapper;
import com.expensive.Expensive.Tracker.repository.RoleRepository;
import com.expensive.Expensive.Tracker.service.RoleService;
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
    public ResponseDTO<RoleResponseDTO> createRole(RoleRequestDTO roleRequestDTO) {

        String normalizedRoleKey =
                getNormalizedRoleKey(roleRequestDTO.getRoleKey());

        if (roleRepository.existsByRoleKey(normalizedRoleKey)) {
            throw new RoleAlreadyExistsException(
                    "Role already exists with key: " + normalizedRoleKey);
        }

        Role role = roleMapper.requestDtoToRole(roleRequestDTO);
        role.setRoleKey(normalizedRoleKey);

        Role savedRole = roleRepository.save(role);

        return ResponseDTO.<RoleResponseDTO>builder()
                .status(HttpStatus.CREATED)
                .message("Role created successfully")
                .data(roleMapper.roleToRoleResponseDto(savedRole))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ResponseDTO<RoleResponseDTO> getRole(Integer roleId) {
        Role role = findOrThrow(roleId);

        return ResponseDTO.<RoleResponseDTO>builder()
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
