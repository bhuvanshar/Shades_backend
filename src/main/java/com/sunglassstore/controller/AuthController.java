package com.sunglassstore.controller;

import com.sunglassstore.dto.request.LoginRequest;
import com.sunglassstore.dto.request.GoogleAuthRequest;
import com.sunglassstore.dto.request.RefreshTokenRequest;
import com.sunglassstore.dto.request.RegisterRequest;
import com.sunglassstore.dto.response.AuthResponse;
import com.sunglassstore.dto.response.MessageResponse;
import com.sunglassstore.dto.response.UserResponse;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authenticationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authenticationService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authenticationService.loginWithGoogle(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authenticationService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@AuthenticationPrincipal SecurityUser principal) {
        authenticationService.logout(principal.getUserId());
        return ResponseEntity.ok(new MessageResponse("Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(authenticationService.getCurrentUser(principal.getUserId()));
    }
}
