package com.sunglassstore.repository;

import com.sunglassstore.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
