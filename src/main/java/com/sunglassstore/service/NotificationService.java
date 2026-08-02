package com.sunglassstore.service;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.entity.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {

    Notification createNotification(Long userId, NotificationType type, String subject, String message);

    Page<Notification> getUserNotifications(Long userId, Pageable pageable);
}
