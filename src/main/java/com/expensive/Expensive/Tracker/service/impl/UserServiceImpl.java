package com.expensive.Expensive.Tracker.service.impl;

import com.expensive.Expensive.Tracker.dto.LoginUserDTO;
import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.UpdateProfileDTO;
import com.expensive.Expensive.Tracker.dto.UserDTO;
import com.expensive.Expensive.Tracker.entity.User;
import com.expensive.Expensive.Tracker.repository.UserRepository;
import com.expensive.Expensive.Tracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public ResponseDTO signup(UserDTO userDto) {
        try{

            Optional<User> emailExists = Optional.ofNullable(userRepository.findByEmail(userDto.getEmail()));
            Optional<User> phoneExists = Optional.ofNullable(userRepository.findByPhone(userDto.getPhone()));

            if(emailExists.isPresent() || phoneExists.isPresent()){
                return new ResponseDTO("failed", HttpStatus.CONFLICT, "user already exists");
            }

            User user = new User();

            user.setFullName(userDto.getFullName());
            user.setEmail(userDto.getEmail());
            user.setPassword(userDto.getPassword());
            user.setPhone(userDto.getPhone());
            user.setRole(userDto.getRole());

            userRepository.save(user);

            return new ResponseDTO("success", HttpStatus.CREATED, "signup successfully.");
        }catch(Exception exception){
            return new ResponseDTO("failed",HttpStatus.BAD_REQUEST,exception.getMessage());
        }
    }

    @Override
    public ResponseDTO login(LoginUserDTO loginUserDTO) {
        return null;
    }

    @Override
    public ResponseDTO updateProfile(UpdateProfileDTO updateProfileDTO) {
        return null;
    }

    @Override
    public ResponseDTO deactivateProfile(Long userId) {
        return null;
    }
}
