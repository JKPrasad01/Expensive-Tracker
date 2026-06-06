package com.expensive.Expensive.Tracker.controller;

import com.expensive.Expensive.Tracker.dto.ResponseDTO;
import com.expensive.Expensive.Tracker.dto.UserDTO;
import com.expensive.Expensive.Tracker.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
@RequestMapping("/app/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ResponseDTO> signup(@Valid @RequestBody UserDTO userDTO){
        ResponseDTO resp = userService.signup(userDTO);
//        return new ResponseEntity<>(resp,resp.getStatusCode());
//        return ResponseEntity.status(resp.getStatusCode().value()).body(resp);
        return ResponseEntity.ok(resp);
    }

}