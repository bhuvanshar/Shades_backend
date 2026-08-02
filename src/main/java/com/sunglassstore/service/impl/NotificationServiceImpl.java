package com.sunglassstore.service.impl;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.NotificationType;
import com.sunglassstore.repository.NotificationRepository;
import com.sunglassstore.service.NotificationService;
import com.sunglassstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    @Override
    @Transactional
    public Notification createNotification(Long userId, NotificationType type,
                                           String subject, String message) {
        User user = userService.findById(userId);
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setNotificationType(type);
        notification.setSubject(subject);
        notification.setMessage(message);
        return notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
