package com.sunglassstore.repository;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailOutbox> findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<EmailOutboxStatus> statuses, LocalDateTime now);
}
