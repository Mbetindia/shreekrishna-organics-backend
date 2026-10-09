

package com.shreekrishna.organics.offer.repository;

import com.shreekrishna.organics.offer.entity.ProductOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductOfferRepository
        extends JpaRepository<ProductOffer, Long> {

    List<ProductOffer> findByProductId(Long productId);

    List<ProductOffer> findByActiveTrue();

    List<ProductOffer>
    findByProductIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long productId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}
