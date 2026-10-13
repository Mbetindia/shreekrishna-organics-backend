
package com.shreekrishna.organics.review.dto;

import com.shreekrishna.organics.review.entity.CustomerReview;
import com.shreekrishna.organics.review.entity.ReviewStatus;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long productId,
        String customerName,
        Integer rating,
        String comment,
        ReviewStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ReviewResponse from(CustomerReview review) {
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getCustomerName(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
