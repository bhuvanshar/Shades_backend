package com.sunglassstore.dto.response;

import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.enums.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long reviewId,
        Long productId,
        Long userId,
        String customerName,
        Integer rating,
        String reviewText,
        ReviewStatus reviewStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse fromEntity(Review review) {
        return new ReviewResponse(review.getReviewId(), review.getProduct().getProductId(),
                review.getUser().getUserId(), review.getUser().getName(), review.getRating(),
                review.getReviewText(), review.getReviewStatus(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
