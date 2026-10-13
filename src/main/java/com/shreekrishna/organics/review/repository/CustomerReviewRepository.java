
package com.shreekrishna.organics.review.repository;

import com.shreekrishna.organics.review.entity.CustomerReview;
import com.shreekrishna.organics.review.entity.ReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerReviewRepository
        extends JpaRepository<CustomerReview, Long> {

    // Product चे सर्व Reviews
    List<CustomerReview> findByProduct_IdOrderByCreatedAtDesc(Long productId);

    // Product चे फक्त Approved Reviews
    List<CustomerReview> findByProduct_IdAndStatusOrderByCreatedAtDesc(
            Long productId,
            ReviewStatus status
    );

    // Admin साठी Status नुसार Reviews
    List<CustomerReview> findByStatusOrderByCreatedAtDesc(
            ReviewStatus status
    );

    // Approved Reviews ची संख्या
    long countByProduct_IdAndStatus(
            Long productId,
            ReviewStatus status
    );
}
