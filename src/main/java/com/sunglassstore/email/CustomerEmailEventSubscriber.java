package com.sunglassstore.email;

import com.sunglassstore.email.event.PasswordResetEmailRequested;
import com.sunglassstore.email.event.RefundCompletedEmailRequested;
import com.sunglassstore.email.event.ReturnStatusEmailRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CustomerEmailEventSubscriber {
    private final EmailOutboxService outboxService;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPasswordReset(PasswordResetEmailRequested event) {
        outboxService.enqueue(new EmailMessage(event.email(), "Reset your Shades World password",
                "Hello " + safeName(event.customerName()) + ",\n\n"
                        + "Use this secure link within 30 minutes to reset your password:\n\n"
                        + normalizedFrontendBaseUrl() + "/signin?resetToken=" + event.rawToken()
                        + "\n\nIf you did not request this, you can ignore this email."),
                LocalDateTime.now().plusMinutes(30));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onReturnStatus(ReturnStatusEmailRequested event) {
        String comments = event.comments() == null || event.comments().isBlank()
                ? "" : "\n\nStore note: " + event.comments();
        outboxService.enqueue(new EmailMessage(event.email(), "Return #" + event.returnId() + " is " + friendly(event.status()),
                "Hello " + safeName(event.customerName()) + ",\n\nYour return request for order #"
                        + event.orderId() + " is now " + friendly(event.status()) + "." + comments
                        + "\n\nYou can view the latest details in My orders."));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRefundCompleted(RefundCompletedEmailRequested event) {
        outboxService.enqueue(new EmailMessage(event.email(), "Refund completed for order #" + event.orderId(),
                "Hello " + safeName(event.customerName()) + ",\n\nYour refund of INR " + event.amount().toPlainString()
                        + " has been completed. Refund reference: #" + event.refundId()
                        + ".\n\nYour bank may take additional time to display the credit."));
    }

    private String safeName(String name) { return name == null || name.isBlank() ? "customer" : name.trim(); }
    private String friendly(String status) { return status.toLowerCase().replace('_', ' '); }
    private String normalizedFrontendBaseUrl() {
        return frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }
}
