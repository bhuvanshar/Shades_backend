package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.UpdateProfileRequest;
import com.sunglassstore.entity.User;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    @Transactional
    public User updateProfile(Long userId, UpdateProfileRequest request) {
        String name = request.getName().trim();
        String phone = request.getPhoneNumber();
        String normalizedPhone = phone == null || phone.isBlank() ? null : phone.trim();
        if (userRepository.updateEditableProfile(userId, name, normalizedPhone) == 0) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }
        return userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
