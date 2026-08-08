package com.expensetracker.auth.impl;

import com.expensetracker.auth.AuthenticationService;
import com.expensetracker.auth.dto.AuthResponse;
import com.expensetracker.auth.dto.LoginRequest;
import com.expensetracker.auth.dto.RegisterRequest;
import com.expensetracker.auth.dto.UpdateProfileRequest;
import com.expensetracker.auth.dto.UserProfileResponse;
import com.expensetracker.entity.Role;
import com.expensetracker.entity.User;
import com.expensetracker.exception.RoleNotFoundException;
import com.expensetracker.exception.UserAlreadyExistsException;
import com.expensetracker.exception.UserNotFoundException;
import com.expensetracker.repository.RoleRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.security.CustomUserDetails;
import com.expensetracker.security.CustomUserDetailsService;
import com.expensetracker.security.JwtService;
import com.expensetracker.security.SecurityConstants;
import com.expensetracker.security.TokenDenylistService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenDenylistService tokenDenylistService;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException("Email already exists.");
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new UserAlreadyExistsException("Phone number already exists.");
        }

        Role userRole = roleRepository.findByRoleKey(SecurityConstants.ROLE_USER)
                .orElseThrow(() ->
                        new RoleNotFoundException(
                                SecurityConstants.ROLE_USER + " role is not seeded"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .isActive(true)
                .roles(List.of(userRole))
                .build();

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException(
                    "Email or phone number already exists.");
        }

        return toAuthResponse(savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest request) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizeEmail(request.getEmail()),
                        request.getPassword()));

        CustomUserDetails principal =
                (CustomUserDetails) authentication.getPrincipal();

        return AuthResponse.builder()
                .token(jwtService.generateToken(
                        principal.getUsername(),
                        principal.getUserId()))
                .tokenType(SecurityConstants.BEARER_PREFIX.trim())
                .expiresInMs(jwtService.getExpirationMs())
                .userId(principal.getUserId())
                .fullName(principal.getFullName())
                .email(principal.getUsername())
                .build();
    }

    @Override
    public void logout(String token) {

        Claims claims;
        try {
            claims = jwtService.parseClaims(token);
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid or expired token");
        }

        long remainingMs = claims.getExpiration().getTime()
                - System.currentTimeMillis();

        tokenDenylistService.deny(
                jwtService.extractJti(claims),
                Duration.ofMillis(Math.max(remainingMs, 0)));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {

        return toProfileResponse(findUserOrThrow(userId));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {

        User user = findUserOrThrow(userId);

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            user.setPhone(request.getPhone());
        }

        if (request.getNewPassword() != null) {

            if (!passwordEncoder.matches(
                    request.getCurrentPassword(),
                    user.getPassword())) {

                throw new BadCredentialsException("Current password is incorrect");
            }

            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        User savedUser = userRepository.save(user);
        customUserDetailsService.evict(savedUser.getEmail());

        return toProfileResponse(savedUser);
    }

    private User findUserOrThrow(Long userId) {

        return userRepository.findById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + userId));
    }

    private AuthResponse toAuthResponse(User user) {

        return AuthResponse.builder()
                .token(jwtService.generateToken(user.getEmail(), user.getId()))
                .tokenType(SecurityConstants.BEARER_PREFIX.trim())
                .expiresInMs(jwtService.getExpirationMs())
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .build();
    }

    private UserProfileResponse toProfileResponse(User user) {

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .active(user.isActive())
                .roles(user.getRoles()
                        .stream()
                        .map(Role::getRoleKey)
                        .toList())
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
