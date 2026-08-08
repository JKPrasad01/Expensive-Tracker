package com.expensetracker.auth;

import com.expensetracker.auth.dto.AuthResponse;
import com.expensetracker.auth.dto.LoginRequest;
import com.expensetracker.auth.dto.RegisterRequest;
import com.expensetracker.auth.dto.UpdateProfileRequest;
import com.expensetracker.auth.dto.UserProfileResponse;
import com.expensetracker.exception.RateLimitExceededException;
import com.expensetracker.security.CustomUserDetails;
import com.expensetracker.security.SecurityConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String LOGIN_KEY_PREFIX = "login:";

    private final AuthenticationService authenticationService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authenticationService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String clientKey = getClientKey(httpRequest);

        if (loginAttemptService.isBlocked(clientKey)
                || loginAttemptService.isBlocked(LOGIN_KEY_PREFIX + request.getEmail())) {

            throw new RateLimitExceededException(
                    "Too many login attempts. Please try again later.");
        }

        try {
            AuthResponse response = authenticationService.login(request);
            loginAttemptService.reset(clientKey);
            loginAttemptService.reset(LOGIN_KEY_PREFIX + request.getEmail());
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException ex) {
            loginAttemptService.recordFailure(clientKey);
            loginAttemptService.recordFailure(LOGIN_KEY_PREFIX + request.getEmail());
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {

        authenticationService.logout(extractToken(authHeader));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(
            @AuthenticationPrincipal CustomUserDetails principal) {

        UserProfileResponse response =
                authenticationService.getProfile(principal.getUserId());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileResponse response =
                authenticationService.updateProfile(principal.getUserId(), request);

        return ResponseEntity.ok(response);
    }

    private String extractToken(String authHeader) {

        if (authHeader == null
                || !authHeader.startsWith(SecurityConstants.BEARER_PREFIX)) {
            throw new BadCredentialsException("Invalid or expired token");
        }

        return authHeader.substring(SecurityConstants.BEARER_PREFIX.length());
    }

    private String getClientKey(HttpServletRequest request) {

        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        return "ip:" + request.getRemoteAddr();
    }
}
