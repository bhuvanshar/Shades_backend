package com.sunglassstore.dto.response;

import com.sunglassstore.entity.User;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
public class UserResponse {

    private Long userId;
    private String email;
    private String name;
    private String phoneNumber;
    private Boolean isActive;
    private Boolean emailVerified;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public static UserResponse fromEntity(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setIsActive(user.getIsActive());
        response.setEmailVerified(user.getEmailVerified());
        response.setRoles(user.getRoles().stream()
                .map(r -> r.getRoleName())
                .collect(Collectors.toSet()));
        response.setCreatedAt(user.getCreatedAt());
        response.setLastLoginAt(user.getLastLoginAt());
        return response;
    }
}
