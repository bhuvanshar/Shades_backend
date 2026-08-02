package com.sunglassstore.repository;

import com.sunglassstore.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(Long orderId);

    Page<Order> findByUserUserIdOrderByPurchasedAtDesc(Long userId, Pageable pageable);

    Optional<Order> findByOrderIdAndUserUserId(Long orderId, Long userId);

    Page<Order> findAllByOrderByPurchasedAtDesc(Pageable pageable);

    long countByUserUserId(Long userId);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.user.userId = :userId AND o.orderStatus <> com.sunglassstore.entity.enums.OrderStatus.CANCELLED")
    BigDecimal sumCompletedValueByUserId(Long userId);

    @Query("SELECT MAX(o.purchasedAt) FROM Order o WHERE o.user.userId = :userId")
    LocalDateTime findLastOrderAtByUserId(Long userId);
}
