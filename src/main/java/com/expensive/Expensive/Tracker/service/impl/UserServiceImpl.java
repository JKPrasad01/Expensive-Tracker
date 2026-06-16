package com.expensive.Expensive.Tracker.service.impl;

import com.expensive.Expensive.Tracker.dto.LoginUserDTO;
import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.UpdateProfileDTO;
import com.expensive.Expensive.Tracker.dto.UserDTO;
import com.expensive.Expensive.Tracker.entity.User;
import com.expensive.Expensive.Tracker.mapper.UserMapper;
import com.expensive.Expensive.Tracker.repository.UserRepository;
import com.expensive.Expensive.Tracker.service.UserService;
import com.expensive.Expensive.Tracker.utils.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtService jwtService;


    @Override
    public ResponseDTO signup(UserDTO userDto) {
        try{
            if(userRepository.existsByEmail(userDto.getEmail()) || userRepository.existsByPhone(userDto.getPhone())){

            }

            User user=userMapper.dtoToUser(userDto);

            userRepository.save(user);

//            return new ResponseDTO("success", HttpStatus.CREATED, "signup successfully.");
        }catch(Exception exception){
//            return new ResponseDTO("failed",HttpStatus.BAD_REQUEST,exception.getMessage());
        }
        return null;
    }

    @Override
    public ResponseDTO login(LoginUserDTO loginUserDTO) {
        return null;
    }

    @Override
    public ResponseDTO updateProfile(UpdateProfileDTO updateProfileDTO, long userId) {
        Optional<User> userExists = userRepository.findById(userId);
        if(!userExists.isEmpty()){
            return ResponseDTO.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .message("User not found.")
                    .build();
        }

        userMapper.updateUserProflieToUser(updateProfileDTO, userExists.get());
        userRepository.save(userExists.get());
        return ResponseDTO.builder()
                .status(HttpStatus.OK)
                .message("user profile updated successfully")
                .build();
    }

    @Override
    public ResponseDTO deactivateProfile(Long userId) {
        return null;
    }
}
