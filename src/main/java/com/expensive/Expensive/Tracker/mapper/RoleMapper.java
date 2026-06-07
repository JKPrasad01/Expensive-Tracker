package com.expensive.Expensive.Tracker.mapper;



import com.expensive.Expensive.Tracker.dto.RoleRequestDTO;
import com.expensive.Expensive.Tracker.dto.RoleResponseDTO;
import com.expensive.Expensive.Tracker.entity.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    Role requestDtoToRole(RoleRequestDTO roleRequestDTO);

    RoleRequestDTO roleToRequestDto(Role role);

    RoleResponseDTO roleToRoleResponseDto(Role role);
}
