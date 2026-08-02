package com.sunglassstore.repository;

import com.sunglassstore.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    @Query("SELECT COALESCE(SUM(r.refundAmount), 0) FROM Refund r " +
           "WHERE r.payment.paymentId = :paymentId " +
           "AND r.refundStatus <> com.sunglassstore.entity.enums.RefundStatus.FAILED")
    BigDecimal sumRefundedByPaymentId(Long paymentId);
}
