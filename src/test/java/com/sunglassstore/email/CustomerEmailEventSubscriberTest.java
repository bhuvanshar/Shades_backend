package com.sunglassstore.email;

import com.sunglassstore.email.event.PasswordResetEmailRequested;
import com.sunglassstore.email.event.RefundCompletedEmailRequested;
import com.sunglassstore.email.event.ReturnStatusEmailRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomerEmailEventSubscriberTest {
    private EmailOutboxService outboxService;
    private CustomerEmailEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        outboxService = mock(EmailOutboxService.class);
        subscriber = new CustomerEmailEventSubscriber(outboxService);
        ReflectionTestUtils.setField(subscriber, "frontendBaseUrl", "http://localhost:3000");
    }

    @Test
    void createsPasswordResetEmailWithFrontendLink() {
        subscriber.onPasswordReset(new PasswordResetEmailRequested("customer@example.com", "Asha", "raw-token"));
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxService).enqueue(captor.capture(), expiry.capture());
        EmailMessage message = captor.getValue();
        assertTrue(message.subject().contains("Reset"));
        assertTrue(message.body().contains("http://localhost:3000/signin?resetToken=raw-token"));
        assertTrue(expiry.getValue().isAfter(LocalDateTime.now().plusMinutes(29)));
    }

    @Test
    void createsReturnStatusEmail() {
        subscriber.onReturnStatus(new ReturnStatusEmailRequested(
                "customer@example.com", "Asha", 4L, 7L, "APPROVED", "Pickup tomorrow"));
        EmailMessage message = capture();
        assertTrue(message.subject().contains("Return #4"));
        assertTrue(message.body().contains("Pickup tomorrow"));
    }

    @Test
    void createsRefundCompletionEmail() {
        subscriber.onRefundCompleted(new RefundCompletedEmailRequested(
                "customer@example.com", "Asha", 8L, 7L, new BigDecimal("3500.00")));
        EmailMessage message = capture();
        assertTrue(message.subject().contains("order #7"));
        assertTrue(message.body().contains("INR 3500.00"));
    }

    private EmailMessage capture() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(outboxService).enqueue(captor.capture());
        return captor.getValue();
    }
}
