
package com.shreekrishna.organics.offer.service;

import com.shreekrishna.organics.offer.entity.DiscountType;
import com.shreekrishna.organics.offer.entity.ProductOffer;
import com.shreekrishna.organics.offer.repository.ProductOfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProductOfferService {

    private final ProductOfferRepository offerRepository;

    public ProductOfferService(ProductOfferRepository offerRepository) {
        this.offerRepository = offerRepository;
    }

    @Transactional
    public ProductOffer createOffer(ProductOffer offer) {

        validateOffer(offer);

        List<ProductOffer> existingOffers =
                offerRepository.findByProductId(offer.getProductId());

        for (ProductOffer existing : existingOffers) {
            if (Boolean.TRUE.equals(existing.getActive())
                    && datesOverlap(existing, offer)) {
                throw new IllegalArgumentException(
                        "Another active offer overlaps this period"
                );
            }
        }

        return offerRepository.save(offer);
    }

    @Transactional(readOnly = true)
    public Optional<ProductOffer> getActiveOffer(Long productId) {

        LocalDateTime now = LocalDateTime.now();

        return offerRepository
                .findByProductIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        productId, now, now
                )
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<ProductOffer> getAllOffers() {
        return offerRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ProductOffer> getOffersByProduct(Long productId) {
        return offerRepository.findByProductId(productId);
    }

    public BigDecimal calculateDiscountedPrice(
            BigDecimal originalPrice,
            ProductOffer offer
    ) {

        if (originalPrice == null ||
                originalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Original price must be non-negative"
            );
        }

        if (offer == null) {
            return originalPrice.setScale(2, RoundingMode.HALF_UP);
        }

        validateDiscount(offer);

        BigDecimal discount;

        if (offer.getDiscountType() == DiscountType.PERCENTAGE) {

            discount = originalPrice
                    .multiply(offer.getDiscountValue())
                    .divide(
                            BigDecimal.valueOf(100),
                            2,
                            RoundingMode.HALF_UP
                    );

        } else {

            discount = offer.getDiscountValue();
        }

        BigDecimal finalPrice = originalPrice.subtract(discount);

        if (finalPrice.compareTo(BigDecimal.ZERO) < 0) {
            finalPrice = BigDecimal.ZERO;
        }

        return finalPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private void validateOffer(ProductOffer offer) {

        if (offer == null || offer.getProductId() == null) {
            throw new IllegalArgumentException(
                    "Product ID is required"
            );
        }

        validateDiscount(offer);

        if (offer.getStartDate() == null ||
                offer.getEndDate() == null ||
                !offer.getEndDate().isAfter(offer.getStartDate())) {
            throw new IllegalArgumentException(
                    "End date must be after start date"
            );
        }

        if (offer.getActive() == null) {
            offer.setActive(true);
        }
    }

    private void validateDiscount(ProductOffer offer) {

        if (offer.getDiscountType() == null ||
                offer.getDiscountValue() == null ||
                offer.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Valid discount type and value are required"
            );
        }

        if (offer.getDiscountType() == DiscountType.PERCENTAGE &&
                offer.getDiscountValue()
                        .compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    "Percentage discount cannot exceed 100"
            );
        }
    }

    private boolean datesOverlap(
            ProductOffer existing,
            ProductOffer incoming
    ) {

        return !incoming.getEndDate().isBefore(existing.getStartDate())
                && !incoming.getStartDate().isAfter(existing.getEndDate());
    }
}
