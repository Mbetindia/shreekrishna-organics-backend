
package com.shreekrishna.organics.product.dto;

import com.shreekrishna.organics.offer.entity.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductVariantResponse {

    private Long id;
    private Long productId;
    private String sku;
    private BigDecimal sizeValue;
    private String sizeUnit;
    private BigDecimal price;
    private BigDecimal mrp;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Hot Deals fields
    private DiscountType discountType;
    private BigDecimal discountValue;
    private BigDecimal discountedPrice;
    private Boolean offerActive;

    // Existing constructor retained for compatibility
    public ProductVariantResponse(
            Long id,
            Long productId,
            String sku,
            BigDecimal sizeValue,
            String sizeUnit,
            BigDecimal price,
            BigDecimal mrp,
            Boolean active,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.sizeValue = sizeValue;
        this.sizeUnit = sizeUnit;
        this.price = price;
        this.mrp = mrp;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        // Default values when no offer is applied
        this.discountType = null;
        this.discountValue = null;
        this.discountedPrice = price;
        this.offerActive = false;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getSizeValue() {
        return sizeValue;
    }

    public String getSizeUnit() {
        return sizeUnit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // Hot Deals getters

    public DiscountType getDiscountType() {
        return discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public BigDecimal getDiscountedPrice() {
        return discountedPrice;
    }

    public Boolean getOfferActive() {
        return offerActive;
    }

    // Hot Deals setters

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public void setDiscountedPrice(BigDecimal discountedPrice) {
        this.discountedPrice = discountedPrice;
    }

    public void setOfferActive(Boolean offerActive) {
        this.offerActive = offerActive;
    }
}
