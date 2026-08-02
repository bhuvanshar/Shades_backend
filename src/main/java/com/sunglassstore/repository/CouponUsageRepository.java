package com.sunglassstore.repository;

import com.sunglassstore.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    long countByCouponCouponId(Long couponId);

    long countByCouponCouponIdAndUserUserId(Long couponId, Long userId);
}
