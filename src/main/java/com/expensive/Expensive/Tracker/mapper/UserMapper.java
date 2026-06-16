package com.expensive.Expensive.Tracker.mapper;

import com.expensive.Expensive.Tracker.dto.UpdateProfileDTO;
import com.expensive.Expensive.Tracker.dto.UserDTO;
import com.expensive.Expensive.Tracker.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User dtoToUser(UserDTO userDTO);

    User dtoToUpdateUser(UpdateProfileDTO updateProfileDTO);

    UserDTO userToDto(User user);

    User updateUserProflieToUser(UpdateProfileDTO updateProfileDTO, @MappingTarget User user);
}
