package com.sunglassstore.repository;

import com.sunglassstore.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCouponCodeIgnoreCase(String couponCode);

    boolean existsByCouponCodeIgnoreCase(String couponCode);

    Page<Coupon> findAllByOrderByCouponIdDesc(Pageable pageable);
}
