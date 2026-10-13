
package com.shreekrishna.organics.review.controller;

import com.shreekrishna.organics.review.dto.ReviewRequest;
import com.shreekrishna.organics.review.dto.ReviewResponse;
import com.shreekrishna.organics.review.service.CustomerReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reviews")
public class AdminReviewController {

    private final CustomerReviewService reviewService;

    public AdminReviewController(CustomerReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ADD REVIEW
    @PostMapping
    public ResponseEntity<ReviewResponse> addReview(
            @Valid @RequestBody ReviewRequest request
    ) {
        ReviewResponse response = ReviewResponse.from(
                reviewService.addReview(
                        request.productId(),
                        request.customerName(),
                        request.rating(),
                        request.comment()
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // EDIT REVIEW
    @PutMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> editReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewRequest request
    ) {
        ReviewResponse response = ReviewResponse.from(
                reviewService.editReview(
                        reviewId,
                        request.customerName(),
                        request.rating(),
                        request.comment()
                )
        );

        return ResponseEntity.ok(response);
    }

    // DELETE REVIEW
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId
    ) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

    // APPROVE REVIEW
    @PatchMapping("/{reviewId}/approve")
    public ResponseEntity<ReviewResponse> approveReview(
            @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(
                ReviewResponse.from(reviewService.approveReview(reviewId))
        );
    }

    // REJECT REVIEW
    @PatchMapping("/{reviewId}/reject")
    public ResponseEntity<ReviewResponse> rejectReview(
            @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(
                ReviewResponse.from(reviewService.rejectReview(reviewId))
        );
    }

    // GET PRODUCT REVIEWS - ADMIN
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<ReviewResponse>> getProductReviews(
            @PathVariable Long productId
    ) {
        List<ReviewResponse> reviews = reviewService
                .getAllReviews(productId)
                .stream()
                .map(ReviewResponse::from)
                .toList();

        return ResponseEntity.ok(reviews);
    }

    // GET SINGLE REVIEW - ADMIN
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(
            @PathVariable Long reviewId
    ) {
        return ResponseEntity.ok(
                ReviewResponse.from(reviewService.getReview(reviewId))
        );
    }
}
