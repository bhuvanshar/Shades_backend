package com.sunglassstore.controller;

import com.sunglassstore.entity.Notification;
import com.sunglassstore.security.SecurityUser;
import com.sunglassstore.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @AuthenticationPrincipal SecurityUser principal, Pageable pageable) {
        return ResponseEntity.ok(notificationService.getUserNotifications(principal.getUserId(), pageable));
    }
}
