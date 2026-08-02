package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateCouponRequest;
import com.sunglassstore.dto.request.ValidateCouponRequest;
import com.sunglassstore.dto.response.CouponValidationResponse;
import com.sunglassstore.entity.Coupon;
import com.sunglassstore.entity.enums.DiscountType;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.InvalidCouponException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.CouponRepository;
import com.sunglassstore.repository.CouponUsageRepository;
import com.sunglassstore.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    @Override
    @Transactional
    public Coupon createCoupon(CreateCouponRequest request) {
        if (couponRepository.existsByCouponCodeIgnoreCase(request.getCouponCode())) {
            throw new ConflictException("Coupon code already exists: " + request.getCouponCode());
        }

        Coupon coupon = new Coupon();
        mapRequestToCoupon(request, coupon);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public Coupon updateCoupon(Long couponId, CreateCouponRequest request) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        mapRequestToCoupon(request, coupon);
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(Long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
        coupon.setIsActive(false);
        couponRepository.save(coupon);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Coupon> getAllCoupons(Pageable pageable) {
        return couponRepository.findAllByOrderByCouponIdDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(Long userId, ValidateCouponRequest request) {
        Coupon coupon = couponRepository.findByCouponCodeIgnoreCase(request.getCouponCode())
                .orElseThrow(() -> new InvalidCouponException("Coupon not found: " + request.getCouponCode()));

        // Check active
        if (!Boolean.TRUE.equals(coupon.getIsActive())) {
            throw new InvalidCouponException("This coupon is no longer active");
        }

        // Check dates
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidFrom()) || now.isAfter(coupon.getValidTo())) {
            throw new InvalidCouponException("This coupon is not valid at this time");
        }

        // Check minimum order amount
        if (request.getOrderAmount().compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw new InvalidCouponException(
                    "Minimum order amount is " + coupon.getMinimumOrderAmount());
        }

        // Check total usage limit
        if (coupon.getUsageLimit() != null) {
            long totalUsage = couponUsageRepository.countByCouponCouponId(coupon.getCouponId());
            if (totalUsage >= coupon.getUsageLimit()) {
                throw new InvalidCouponException("This coupon has reached its usage limit");
            }
        }

        // Check per-user usage limit
        if (coupon.getUsageLimitPerUser() != null) {
            long userUsage = couponUsageRepository.countByCouponCouponIdAndUserUserId(
                    coupon.getCouponId(), userId);
            if (userUsage >= coupon.getUsageLimitPerUser()) {
                throw new InvalidCouponException("You have already used this coupon the maximum number of times");
            }
        }

        BigDecimal discount = calculateDiscount(coupon, request.getOrderAmount());

        return new CouponValidationResponse(true, coupon.getCouponCode(),
                coupon.getDiscountType().name(), coupon.getDiscountValue(),
                discount, "Coupon is valid");
    }

    @Override
    public BigDecimal calculateDiscount(Coupon coupon, BigDecimal orderAmount) {
        BigDecimal discount;

        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = orderAmount.multiply(coupon.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            discount = coupon.getDiscountValue();
        }

        // Cap at maximum discount amount if set
        if (coupon.getMaximumDiscountAmount() != null &&
                discount.compareTo(coupon.getMaximumDiscountAmount()) > 0) {
            discount = coupon.getMaximumDiscountAmount();
        }

        // Discount cannot exceed order amount
        if (discount.compareTo(orderAmount) > 0) {
            discount = orderAmount;
        }

        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private void mapRequestToCoupon(CreateCouponRequest request, Coupon coupon) {
        coupon.setCouponCode(request.getCouponCode().toUpperCase().trim());
        coupon.setDescription(request.getDescription());
        coupon.setDiscountType(DiscountType.valueOf(request.getDiscountType()));
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMinimumOrderAmount(request.getMinimumOrderAmount());
        coupon.setMaximumDiscountAmount(request.getMaximumDiscountAmount());
        coupon.setUsageLimit(request.getUsageLimit());
        coupon.setUsageLimitPerUser(request.getUsageLimitPerUser());
        coupon.setValidFrom(request.getValidFrom());
        coupon.setValidTo(request.getValidTo());
    }
}
