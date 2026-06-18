package com.expensive.Expensive.Tracker.controller;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.UpdateProfileDTO;
import com.expensive.Expensive.Tracker.dto.UserDTO;
import com.expensive.Expensive.Tracker.service.UserService;
import com.expensive.Expensive.Tracker.utils.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/app/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    @PostMapping("/signup")
    public ResponseEntity<ResponseDTO> signup(@Valid @RequestBody UserDTO userDTO){
        ResponseDTO resp = userService.signup(userDTO);
        return ResponseEntity.status(resp.getStatus().value()).body(resp);
    }

    public ResponseEntity<ResponseDTO> update(@Valid @RequestBody UpdateProfileDTO updateProfileDTO, @RequestHeader("Authorization") String authHeader){
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).
                    body(
                            ResponseDTO.builder()
                                    .status(HttpStatus.UNAUTHORIZED)
                                    .message("Invaild token")
                                    .build()
                    );
        }
        String token = authHeader.substring(8, authHeader.length());
        long userId = jwtService.extractUserId(token);
        ResponseDTO resp = userService.updateProfile(updateProfileDTO,userId);
        return ResponseEntity.status(resp.getStatus().value()).body(resp);
    }

}