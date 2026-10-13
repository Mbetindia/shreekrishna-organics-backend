
package com.shreekrishna.organics.review.controller;

import com.shreekrishna.organics.review.dto.ReviewResponse;
import com.shreekrishna.organics.review.service.CustomerReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products/{productId}/reviews")
public class ProductReviewController {

    private final CustomerReviewService reviewService;

    public ProductReviewController(CustomerReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // GET APPROVED REVIEWS
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> getApprovedReviews(
            @PathVariable Long productId
    ) {
        List<ReviewResponse> reviews = reviewService
                .getApprovedReviews(productId)
                .stream()
                .map(ReviewResponse::from)
                .toList();

        return ResponseEntity.ok(reviews);
    }

    // GET AVERAGE RATING AND REVIEW COUNT
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getReviewSummary(
            @PathVariable Long productId
    ) {
        double averageRating = reviewService.getAverageRating(productId);
        long reviewCount = reviewService.getReviewCount(productId);

        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "averageRating", Math.round(averageRating * 10.0) / 10.0,
                "reviewCount", reviewCount
        ));
    }
}
