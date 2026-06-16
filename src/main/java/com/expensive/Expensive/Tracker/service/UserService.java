package com.expensive.Expensive.Tracker.service;

import com.expensive.Expensive.Tracker.dto.LoginUserDTO;
import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.UpdateProfileDTO;
import com.expensive.Expensive.Tracker.dto.UserDTO;

public interface UserService {
    ResponseDTO signup(UserDTO userDto);
    ResponseDTO login(LoginUserDTO loginUserDTO);
    ResponseDTO updateProfile(UpdateProfileDTO updateProfileDTO, long userId);
    ResponseDTO deactivateProfile(Long userId);
}
