package com.expensetracker.mapper;



import com.expensetracker.dto.RoleRequestDTO;
import com.expensetracker.dto.RoleResponseDTO;
import com.expensetracker.entity.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    Role requestDtoToRole(RoleRequestDTO roleRequestDTO);

    RoleRequestDTO roleToRequestDto(Role role);

    RoleResponseDTO roleToRoleResponseDto(Role role);
}
