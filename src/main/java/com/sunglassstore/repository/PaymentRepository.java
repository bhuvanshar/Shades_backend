package com.sunglassstore.repository;

import com.sunglassstore.entity.Payment;
import com.sunglassstore.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByOrderOrderId(Long orderId, Pageable pageable);

    Optional<Payment> findFirstByOrderOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);
    List<Payment> findByOrderOrderIdOrderByCreatedAtDesc(Long orderId);
}
