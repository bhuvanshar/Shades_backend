package com.sunglassstore.service;

import com.sunglassstore.entity.User;

public interface UserService {

    User findById(Long userId);

    User findByEmail(String email);
}
