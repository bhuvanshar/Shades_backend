package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.LoginRequest;
import com.sunglassstore.dto.request.GoogleAuthRequest;
import com.sunglassstore.dto.request.RefreshTokenRequest;
import com.sunglassstore.dto.request.RegisterRequest;
import com.sunglassstore.dto.response.AuthResponse;
import com.sunglassstore.dto.response.UserResponse;
import com.sunglassstore.entity.LoginAttempt;
import com.sunglassstore.entity.RefreshToken;
import com.sunglassstore.entity.Role;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.exception.UnauthorizedException;
import com.sunglassstore.repository.LoginAttemptRepository;
import com.sunglassstore.repository.RefreshTokenRepository;
import com.sunglassstore.repository.RoleRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.security.JwtService;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.Collections;
import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName().trim());
        user.setPhoneNumber(request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()
                ? null : request.getPhoneNumber().trim());
        user.setPasswordChangedAt(LocalDateTime.now());

        Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER role not found. Run data initialization."));
        user.getRoles().add(customerRole);

        user = userRepository.save(user);

        SecurityUser securityUser = new SecurityUser(user);
        String accessToken = jwtService.generateAccessToken(securityUser);
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plus(java.time.Duration.ofMillis(jwtService.getRefreshTokenExpiration())));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                user.getUserId(), user.getEmail(), user.getName());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();

            // Record successful login
            recordLoginAttempt(request.getEmail(), null, true, null);

            // Update last login
            User user = userRepository.findById(securityUser.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            user.setLastLoginAt(LocalDateTime.now());
            user.setFailedLoginAttempts(0);
            userRepository.save(user);

            // Generate tokens
            String accessToken = jwtService.generateAccessToken(securityUser);
            String rawRefreshToken = UUID.randomUUID().toString();

            // Store hashed refresh token
            RefreshToken refreshToken = new RefreshToken();
            refreshToken.setUser(user);
            refreshToken.setTokenHash(hashToken(rawRefreshToken));
            refreshToken.setExpiresAt(LocalDateTime.now().plus(java.time.Duration.ofMillis(jwtService.getRefreshTokenExpiration())));
            
            refreshTokenRepository.save(refreshToken);

            return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                    user.getUserId(), user.getEmail(), user.getName());

        } catch (BadCredentialsException e) {
            recordLoginAttempt(request.getEmail(), null, false, "Invalid credentials");
            throw e;
        }
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new UnauthorizedException("Google sign-in is not configured");
        }

        GoogleIdToken idToken;
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
            idToken = verifier.verify(request.getCredential());
        } catch (GeneralSecurityException | IOException exception) {
            throw new UnauthorizedException("Unable to verify Google account");
        }

        if (idToken == null) {
            throw new UnauthorizedException("Invalid Google credential");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail() == null ? "" : payload.getEmail().trim().toLowerCase();
        if (!Boolean.TRUE.equals(payload.getEmailVerified()) || !email.endsWith("@gmail.com")) {
            throw new UnauthorizedException("Please use a verified Gmail account");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(payload.get("name") instanceof String name && !name.isBlank() ? name.trim() : email);
            newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setEmailVerified(true);
            newUser.setPasswordChangedAt(LocalDateTime.now());
            Role customerRole = roleRepository.findByRoleName("CUSTOMER")
                    .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER role not found. Run data initialization."));
            newUser.getRoles().add(customerRole);
            return userRepository.save(newUser);
        });

        if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getAccountLocked())) {
            throw new UnauthorizedException("This customer account is unavailable");
        }
        if (user.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getRoleName()))) {
            throw new UnauthorizedException("Administrators must sign in with their password");
        }

        user.setEmailVerified(true);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        SecurityUser securityUser = new SecurityUser(user);
        String accessToken = jwtService.generateAccessToken(securityUser);
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hashToken(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now().plus(
                java.time.Duration.ofMillis(jwtService.getRefreshTokenExpiration())));
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, rawRefreshToken, "Bearer",
                user.getUserId(), user.getEmail(), user.getName());
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!storedToken.isActive()) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        // Revoke old token
        storedToken.setRevokedAt(LocalDateTime.now());

        // Create new refresh token (rotation)
        String newRawToken = UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawToken);
        storedToken.setReplacedByTokenHash(newTokenHash);
        refreshTokenRepository.save(storedToken);

        User user = storedToken.getUser();
        SecurityUser securityUser = new SecurityUser(user);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUser(user);
        newRefreshToken.setTokenHash(newTokenHash);
        newRefreshToken.setExpiresAt(LocalDateTime.now().plus(java.time.Duration.ofMillis(jwtService.getRefreshTokenExpiration())));
        refreshTokenRepository.save(newRefreshToken);

        String accessToken = jwtService.generateAccessToken(securityUser);

        return new AuthResponse(accessToken, newRawToken, "Bearer",
                user.getUserId(), user.getEmail(), user.getName());
    }

    @Override
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        // Map while the transaction is open so lazy roles can be read safely.
        return UserResponse.fromEntity(user);
    }

    private void recordLoginAttempt(String email, String ipAddress, boolean success, String failureReason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmail(email);
        attempt.setIpAddress(ipAddress);
        attempt.setIsSuccessful(success);
        attempt.setFailureReason(failureReason);
        loginAttemptRepository.save(attempt);
    }

    /**
     * SHA-256 hash of the raw refresh token.
     * We never store the raw token in the database.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
