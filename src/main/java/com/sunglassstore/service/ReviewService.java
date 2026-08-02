package com.sunglassstore.service;

import com.sunglassstore.dto.request.CreateReviewRequest;
import com.sunglassstore.dto.request.UpdateReviewRequest;
import com.sunglassstore.dto.response.ReviewResponse;
import com.sunglassstore.entity.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    ReviewResponse createReview(Long userId, CreateReviewRequest request);
    ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request);
    void deleteReview(Long userId, Long reviewId);
    Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable);
    ReviewResponse getMyProductReview(Long userId, Long productId);
    ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus status);
}
