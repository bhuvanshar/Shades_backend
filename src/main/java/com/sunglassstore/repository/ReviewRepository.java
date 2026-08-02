package com.sunglassstore.repository;

import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductProductIdAndReviewStatus(Long productId, ReviewStatus status, Pageable pageable);

    Optional<Review> findByReviewIdAndUserUserId(Long reviewId, Long userId);

    List<Review> findByUserUserIdAndProductProductIdOrderByCreatedAtDesc(Long userId, Long productId);

    boolean existsByUserUserIdAndProductProductId(Long userId, Long productId);
    boolean existsByUserUserIdAndOrderItemOrderItemId(Long userId, Long orderItemId);
}
