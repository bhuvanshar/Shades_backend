package com.sunglassstore.email;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.repository.EmailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailOutboxDeliveryService {
    private final EmailOutboxRepository repository;
    private final EmailService emailService;

    @Value("${app.email.outbox.max-attempts:8}")
    private int maxAttempts;

    @Transactional
    public boolean deliverNext() {
        EmailOutbox email = repository
                .findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(EmailOutboxStatus.PENDING, EmailOutboxStatus.RETRY), LocalDateTime.now())
                .orElse(null);
        if (email == null) return false;

        if (email.getExpiresAt() != null && !email.getExpiresAt().isAfter(LocalDateTime.now())) {
            email.setStatus(EmailOutboxStatus.FAILED);
            email.setLastError("Email expired before delivery");
            scrubPayload(email);
            repository.save(email);
            return true;
        }

        try {
            emailService.send(new EmailMessage(email.getRecipient(), email.getSubject(), email.getBody()));
            email.setStatus(EmailOutboxStatus.SENT);
            email.setSentAt(LocalDateTime.now());
            email.setLastError(null);
            scrubPayload(email);
        } catch (RuntimeException exception) {
            int attempts = email.getAttemptCount() + 1;
            email.setAttemptCount(attempts);
            email.setLastError(truncate(exception.getMessage()));
            if (attempts >= maxAttempts) {
                email.setStatus(EmailOutboxStatus.FAILED);
                scrubPayload(email);
            } else {
                email.setStatus(EmailOutboxStatus.RETRY);
                long delayMinutes = Math.min(1L << Math.min(attempts - 1, 10), 24L * 60L);
                email.setNextAttemptAt(LocalDateTime.now().plusMinutes(delayMinutes));
            }
        }
        repository.save(email);
        return true;
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) return "Email delivery failed";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private void scrubPayload(EmailOutbox email) {
        email.setBody("");
    }
}
