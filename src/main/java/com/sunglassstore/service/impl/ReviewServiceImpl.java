package com.sunglassstore.service.impl;

import com.sunglassstore.dto.request.CreateReviewRequest;
import com.sunglassstore.dto.request.UpdateReviewRequest;
import com.sunglassstore.dto.response.ReviewResponse;
import com.sunglassstore.entity.Product;
import com.sunglassstore.entity.Review;
import com.sunglassstore.entity.User;
import com.sunglassstore.entity.enums.ReviewStatus;
import com.sunglassstore.exception.BadRequestException;
import com.sunglassstore.exception.ConflictException;
import com.sunglassstore.exception.ResourceNotFoundException;
import com.sunglassstore.repository.OrderItemRepository;
import com.sunglassstore.repository.ProductRepository;
import com.sunglassstore.repository.ReviewRepository;
import com.sunglassstore.repository.UserRepository;
import com.sunglassstore.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        // Verify user has purchased the product (delivered order)
        boolean hasPurchased = orderItemRepository.hasUserPurchasedProduct(userId, request.getProductId());
        if (!hasPurchased) {
            throw new BadRequestException("You can only review products you have purchased and received");
        }

        // Check if user already reviewed this product
        if (reviewRepository.existsByUserUserIdAndProductProductId(userId, request.getProductId())) {
            throw new ConflictException("You have already reviewed this product");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setRating(request.getRating());
        review.setReviewText(cleanReviewText(request.getReviewText()));
        review.setReviewStatus(ReviewStatus.APPROVED);

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, UpdateReviewRequest request) {
        Review review = reviewRepository.findByReviewIdAndUserUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));

        review.setRating(request.getRating());
        review.setReviewText(cleanReviewText(request.getReviewText()));
        review.setReviewStatus(ReviewStatus.APPROVED);

        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    @Override
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = reviewRepository.findByReviewIdAndUserUserId(reviewId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        reviewRepository.delete(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getProductReviews(Long productId, Pageable pageable) {
        return reviewRepository.findByProductProductIdAndReviewStatus(productId, ReviewStatus.APPROVED, pageable)
                .map(ReviewResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getMyProductReview(Long userId, Long productId) {
        return reviewRepository.findByUserUserIdAndProductProductId(userId, productId)
                .map(ReviewResponse::fromEntity).orElse(null);
    }

    @Override
    @Transactional
    public ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus status) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        review.setReviewStatus(status);
        return ReviewResponse.fromEntity(reviewRepository.save(review));
    }

    private String cleanReviewText(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
