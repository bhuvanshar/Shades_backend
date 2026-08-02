package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.Refund;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.entity.enums.RefundStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.repository.RefundRepository;
import com.sunglassstore.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public Refund processRefund(Long paymentId, CreateRefundRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Refunds can only be processed for paid payments");
        }

        // Check refund amount doesn't exceed what's available
        BigDecimal alreadyRefunded = refundRepository.sumRefundedByPaymentId(paymentId);
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }

        BigDecimal maxRefundable = payment.getAmount().subtract(alreadyRefunded);
        if (request.getRefundAmount().compareTo(maxRefundable) > 0) {
            throw new BadRequestException(
                    "Refund amount exceeds available balance. Maximum refundable: " + maxRefundable);
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setRefundAmount(request.getRefundAmount());
        refund.setReason(request.getReason());
        refund.setRefundStatus(RefundStatus.COMPLETED); // Mock processor always succeeds

        Refund savedRefund = refundRepository.save(refund);

        // Update payment status
        BigDecimal totalRefunded = alreadyRefunded.add(request.getRefundAmount());
        if (totalRefunded.compareTo(payment.getAmount()) >= 0) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        return savedRefund;
    }
}
