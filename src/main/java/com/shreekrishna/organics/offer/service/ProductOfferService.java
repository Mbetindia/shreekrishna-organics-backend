package com.shreekrishna.organics.offer.service;

import com.shreekrishna.organics.exception.ResourceNotFoundException;
import com.shreekrishna.organics.offer.entity.DiscountType;
import com.shreekrishna.organics.offer.entity.ProductOffer;
import com.shreekrishna.organics.offer.repository.ProductOfferRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        if (Boolean.TRUE.equals(offer.getActive())) {
            checkOverlappingOffers(offer, null);
        }
        return offerRepository.save(offer);
    }

    @Transactional(readOnly = true)
    public Optional<ProductOffer> getActiveOffer(Long productId) {
        LocalDateTime now = LocalDateTime.now();
        return offerRepository
                .findByProductIdAndActiveTrueAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        productId, now, now)
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

    @Transactional(readOnly = true)
    public ProductOffer getOfferById(Long id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + id));
    }

    @Transactional
    public ProductOffer updateOffer(Long id, ProductOffer request) {
        ProductOffer existing = getOfferById(id);
        if (request == null) {
            throw new IllegalArgumentException("Offer details are required");
        }
        if (request.getProductId() != null
                && !request.getProductId().equals(existing.getProductId())) {
            throw new IllegalArgumentException("Product ID cannot be changed");
        }

        ProductOffer proposed = new ProductOffer();
        proposed.setProductId(existing.getProductId());
        proposed.setDiscountType(request.getDiscountType());
        proposed.setDiscountValue(request.getDiscountValue());
        proposed.setStartDate(request.getStartDate());
        proposed.setEndDate(request.getEndDate());
        proposed.setActive(existing.getActive());
        validateOffer(proposed);

        if (Boolean.TRUE.equals(existing.getActive())) {
            checkOverlappingOffers(proposed, id);
        }

        existing.setDiscountType(proposed.getDiscountType());
        existing.setDiscountValue(proposed.getDiscountValue());
        existing.setStartDate(proposed.getStartDate());
        existing.setEndDate(proposed.getEndDate());
        return offerRepository.save(existing);
    }

    @Transactional
    public ProductOffer activateOffer(Long id) {
        ProductOffer offer = getOfferById(id);
        validateOffer(offer);
        checkOverlappingOffers(offer, id);
        if (!Boolean.TRUE.equals(offer.getActive())) {
            offer.setActive(true);
            return offerRepository.save(offer);
        }
        return offer;
    }

    @Transactional
    public ProductOffer deactivateOffer(Long id) {
        ProductOffer offer = getOfferById(id);
        if (Boolean.TRUE.equals(offer.getActive())) {
            offer.setActive(false);
            return offerRepository.save(offer);
        }
        return offer;
    }

    public BigDecimal calculateDiscountedPrice(BigDecimal originalPrice, ProductOffer offer) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Original price must be non-negative");
        }
        if (offer == null) {
            return originalPrice.setScale(2, RoundingMode.HALF_UP);
        }
        validateDiscount(offer);
        BigDecimal discount;
        if (offer.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = originalPrice.multiply(offer.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
            throw new IllegalArgumentException("Product ID is required");
        }
        validateDiscount(offer);
        if (offer.getStartDate() == null || offer.getEndDate() == null
                || !offer.getEndDate().isAfter(offer.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        if (offer.getActive() == null) {
            offer.setActive(true);
        }
    }

    private void validateDiscount(ProductOffer offer) {
        if (offer.getDiscountType() == null || offer.getDiscountValue() == null
                || offer.getDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Valid discount type and value are required");
        }
        if (offer.getDiscountType() == DiscountType.PERCENTAGE
                && offer.getDiscountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discount cannot exceed 100");
        }
    }

    private void checkOverlappingOffers(ProductOffer incoming, Long excludeOfferId) {
        List<ProductOffer> offers = offerRepository.findByProductId(incoming.getProductId());
        for (ProductOffer existing : offers) {
            if (excludeOfferId != null && excludeOfferId.equals(existing.getId())) {
                continue;
            }
            if (Boolean.TRUE.equals(existing.getActive()) && datesOverlap(existing, incoming)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Another active offer overlaps this period");
            }
        }
    }

    private boolean datesOverlap(ProductOffer existing, ProductOffer incoming) {
        return !incoming.getEndDate().isBefore(existing.getStartDate())
                && !incoming.getStartDate().isAfter(existing.getEndDate());
    }
}
