package com.sunglassstore.email;

import com.sunglassstore.entity.EmailOutbox;
import com.sunglassstore.entity.enums.EmailOutboxStatus;
import com.sunglassstore.repository.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailOutboxDeliveryServiceTest {
    private EmailOutboxRepository repository;
    private EmailService emailService;
    private EmailOutboxDeliveryService delivery;

    @BeforeEach
    void setUp() {
        repository = mock(EmailOutboxRepository.class);
        emailService = mock(EmailService.class);
        delivery = new EmailOutboxDeliveryService(repository, emailService);
        ReflectionTestUtils.setField(delivery, "maxAttempts", 3);
    }

    @Test
    void successfulDeliveryMarksMessageSent() {
        EmailOutbox email = queued(0);
        when(repository.findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.of(email));

        assertTrue(delivery.deliverNext());

        assertEquals(EmailOutboxStatus.SENT, email.getStatus());
        assertNotNull(email.getSentAt());
        assertEquals("", email.getBody());
        verify(emailService).send(any(EmailMessage.class));
        verify(repository).save(email);
    }

    @Test
    void temporaryFailureSchedulesRetryWithError() {
        EmailOutbox email = queued(0);
        when(repository.findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.of(email));
        doThrow(new EmailDeliveryException("SMTP down", new RuntimeException()))
                .when(emailService).send(any());

        assertTrue(delivery.deliverNext());

        assertEquals(EmailOutboxStatus.RETRY, email.getStatus());
        assertEquals(1, email.getAttemptCount());
        assertEquals("SMTP down", email.getLastError());
        assertTrue(email.getNextAttemptAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void finalFailureStopsAutomaticRetry() {
        EmailOutbox email = queued(2);
        when(repository.findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.of(email));
        doThrow(new EmailDeliveryException("Rejected", new RuntimeException()))
                .when(emailService).send(any());

        delivery.deliverNext();

        assertEquals(EmailOutboxStatus.FAILED, email.getStatus());
        assertEquals(3, email.getAttemptCount());
        assertEquals("", email.getBody());
    }

    @Test
    void expiredSensitiveEmailIsNeverSentAndPayloadIsScrubbed() {
        EmailOutbox email = queued(0);
        email.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findFirstByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any(), any()))
                .thenReturn(Optional.of(email));

        assertTrue(delivery.deliverNext());

        assertEquals(EmailOutboxStatus.FAILED, email.getStatus());
        assertEquals("", email.getBody());
        assertEquals("Email expired before delivery", email.getLastError());
        verifyNoInteractions(emailService);
    }

    private EmailOutbox queued(int attempts) {
        EmailOutbox email = new EmailOutbox();
        email.setRecipient("customer@example.com");
        email.setSubject("Subject");
        email.setBody("Body");
        email.setAttemptCount(attempts);
        email.setStatus(attempts == 0 ? EmailOutboxStatus.PENDING : EmailOutboxStatus.RETRY);
        email.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return email;
    }
}
