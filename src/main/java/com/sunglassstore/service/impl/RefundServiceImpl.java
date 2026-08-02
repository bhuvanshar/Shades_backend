package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateRefundRequest;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.Refund;
import com.sunglassstore.entity.ReturnRequest;
import com.sunglassstore.entity.enums.ReturnStatus;
import com.sunglassstore.dto.response.RefundResponse;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.entity.enums.RefundStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.repository.RefundRepository;
import com.sunglassstore.repository.ReturnRequestRepository;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public RefundResponse processRefund(Long paymentId, CreateRefundRequest request) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getPaymentStatus() != PaymentStatus.PAID && payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BadRequestException("Refunds can only be processed for paid payments");
        }

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(request.getReturnId())
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found"));
        if (!returnRequest.getOrder().getOrderId().equals(payment.getOrder().getOrderId())) {
            throw new BadRequestException("Return request does not belong to this payment's order");
        }
        Long orderId = returnRequest.getOrder().getOrderId();
        orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if (returnRequest.getReturnStatus() != ReturnStatus.RECEIVED && returnRequest.getReturnStatus() != ReturnStatus.COMPLETED) {
            throw new BadRequestException("Refund can be issued only after returned items are received");
        }

        // Check refund amount doesn't exceed what's available
        BigDecimal alreadyRefunded = refundRepository.sumRefundedByPaymentId(paymentId);
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }

        BigDecimal paymentRemaining = payment.getAmount().subtract(alreadyRefunded);
        BigDecimal returnedGrossValue = returnRequest.getItems().stream()
                .map(item -> item.getOrderItem().getLineTotal()
                        .multiply(BigDecimal.valueOf(item.getQuantity()))
                        .divide(BigDecimal.valueOf(item.getOrderItem().getQuantity()), 10, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderSubtotal = returnRequest.getOrder().getSubtotalAmount();
        if (orderSubtotal == null || orderSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Order subtotal is invalid; refund cannot be calculated");
        }
        BigDecimal shippingAmount = returnRequest.getOrder().getShippingAmount() == null
                ? BigDecimal.ZERO : returnRequest.getOrder().getShippingAmount();
        BigDecimal paidMerchandiseValue = returnRequest.getOrder().getTotalAmount().subtract(shippingAmount);
        BigDecimal returnValue = paidMerchandiseValue
                .multiply(returnedGrossValue)
                .divide(orderSubtotal, 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO);
        BigDecimal refundedForReturn = refundRepository.sumRefundedByReturnId(returnRequest.getReturnId());
        if (refundedForReturn == null) refundedForReturn = BigDecimal.ZERO;
        BigDecimal returnRemaining = returnValue.subtract(refundedForReturn);
        BigDecimal refundedForOrder = refundRepository.sumRefundedByOrderId(orderId);
        if (refundedForOrder == null) refundedForOrder = BigDecimal.ZERO;
        BigDecimal merchandiseRemaining = paidMerchandiseValue.subtract(refundedForOrder);
        BigDecimal maxRefundable = paymentRemaining.min(returnRemaining).min(merchandiseRemaining).max(BigDecimal.ZERO);
        if (request.getRefundAmount().compareTo(maxRefundable) > 0) {
            throw new BadRequestException(
                    "Refund amount exceeds available balance. Maximum refundable: " + maxRefundable);
        }

        Refund refund = new Refund();
        refund.setPayment(payment);
        refund.setReturnRequest(returnRequest);
        refund.setRefundAmount(request.getRefundAmount());
        refund.setReason(request.getReason().trim());
        refund.setRefundStatus(RefundStatus.COMPLETED); // Mock processor always succeeds
        refund.setProcessedAt(java.time.LocalDateTime.now());

        Refund savedRefund = refundRepository.save(refund);

        // Update payment status
        BigDecimal totalRefunded = alreadyRefunded.add(request.getRefundAmount());
        if (totalRefunded.compareTo(payment.getAmount()) >= 0) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        return RefundResponse.fromEntity(savedRefund);
    }
}
