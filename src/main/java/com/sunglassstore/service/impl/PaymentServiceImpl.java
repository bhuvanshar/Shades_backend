package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.PaymentRequest;
import com.sunglassstore.dto.response.PaymentResult;
import com.sunglassstore.entity.Order;
import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.enums.OrderStatus;
import com.sunglassstore.entity.enums.PaymentStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.OrderRepository;
import com.sunglassstore.repository.PaymentRepository;
import com.sunglassstore.service.OrderService;
import com.sunglassstore.service.PaymentProcessor;
import com.sunglassstore.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentProcessor paymentProcessor;
    private final OrderService orderService;

    @Override
    @Transactional
    public Payment processPayment(Long userId, Long orderId, PaymentRequest request) {
        Order order = orderRepository.findByOrderIdAndUserUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (order.getOrderStatus() != OrderStatus.PLACED) {
            throw new BadRequestException("Payment can only be processed for orders in PLACED status");
        }

        // Check if a successful payment already exists
        paymentRepository.findFirstByOrderOrderIdAndPaymentStatus(orderId, PaymentStatus.PAID)
                .ifPresent(p -> {
                    throw new BadRequestException("Order has already been paid");
                });

        // Process via payment processor
        PaymentResult result = paymentProcessor.process(request.getPaymentMethod(), order.getTotalAmount());

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setAmount(order.getTotalAmount());
        payment.setProviderReference(result.getProviderReference());

        if (result.isSuccess()) {
            payment.setPaymentStatus(PaymentStatus.PAID);
            orderService.updateOrderStatus(orderId, OrderStatus.CONFIRMED, "Payment received");
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Payment> getPayments(Long orderId, Pageable pageable) {
        return paymentRepository.findByOrderOrderId(orderId, pageable);
    }
}
