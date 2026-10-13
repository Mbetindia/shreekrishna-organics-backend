
package com.shreekrishna.organics.review.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.product.entity.Product;
import com.shreekrishna.organics.product.repository.ProductRepository;
import com.shreekrishna.organics.review.entity.CustomerReview;
import com.shreekrishna.organics.review.entity.ReviewStatus;
import com.shreekrishna.organics.review.repository.CustomerReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerReviewService {

    private final CustomerReviewRepository reviewRepository;
    private final ProductRepository productRepository;

    public CustomerReviewService(
            CustomerReviewRepository reviewRepository,
            ProductRepository productRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public CustomerReview addReview(
            Long productId,
            String customerName,
            Integer rating,
            String comment
    ) {
        validateReview(customerName, rating);

        Product product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product not found: " + productId
                        )
                );

        CustomerReview review = new CustomerReview();
        review.setProduct(product);
        review.setCustomerName(customerName.trim());
        review.setRating(rating);
        review.setComment(comment);
        review.setStatus(ReviewStatus.PENDING);

        return reviewRepository.save(review);
    }

    @Transactional
    public CustomerReview editReview(
            Long reviewId,
            String customerName,
            Integer rating,
            String comment
    ) {
        validateReview(customerName, rating);

        CustomerReview review = getReview(reviewId);

        review.setCustomerName(customerName.trim());
        review.setRating(rating);
        review.setComment(comment);
        review.setStatus(ReviewStatus.PENDING);

        return reviewRepository.save(review);
    }

    @Transactional
    public void deleteReview(Long reviewId) {
        CustomerReview review = getReview(reviewId);
        reviewRepository.delete(review);
    }

    @Transactional
    public CustomerReview approveReview(Long reviewId) {
        CustomerReview review = getReview(reviewId);
        review.setStatus(ReviewStatus.APPROVED);
        return reviewRepository.save(review);
    }

    @Transactional
    public CustomerReview rejectReview(Long reviewId) {
        CustomerReview review = getReview(reviewId);
        review.setStatus(ReviewStatus.REJECTED);
        return reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public List<CustomerReview> getApprovedReviews(Long productId) {
        validateProductExists(productId);

        return reviewRepository
                .findByProduct_IdAndStatusOrderByCreatedAtDesc(
                        productId,
                        ReviewStatus.APPROVED
                );
    }

    @Transactional(readOnly = true)
    public long getReviewCount(Long productId) {
        validateProductExists(productId);

        return reviewRepository.countByProduct_IdAndStatus(
                productId,
                ReviewStatus.APPROVED
        );
    }

    @Transactional(readOnly = true)
    public double getAverageRating(Long productId) {
        List<CustomerReview> reviews = getApprovedReviews(productId);

        return reviews.stream()
                .mapToInt(CustomerReview::getRating)
                .average()
                .orElse(0.0);
    }

    @Transactional(readOnly = true)
    public List<CustomerReview> getAllReviews(Long productId) {
        validateProductExists(productId);

        return reviewRepository
                .findByProduct_IdOrderByCreatedAtDesc(productId);
    }

    @Transactional(readOnly = true)
    public CustomerReview getReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Review not found: " + reviewId
                        )
                );
    }

    private void validateProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException(
                    "Product not found: " + productId
            );
        }
    }

    private void validateReview(String customerName, Integer rating) {
        if (customerName == null ||
                customerName.isBlank() ||
                customerName.trim().length() > 150) {
            throw new IllegalArgumentException(
                    "Customer name must be between 1 and 150 characters"
            );
        }

        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException(
                    "Rating must be between 1 and 5"
            );
        }
    }
}
