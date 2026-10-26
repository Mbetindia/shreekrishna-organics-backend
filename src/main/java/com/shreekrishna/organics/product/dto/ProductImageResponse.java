package com.shreekrishna.organics.product.dto;

import java.time.LocalDateTime;

public class ProductImageResponse {

    private final Long id;
    private final Long productId;

    // NEW: Variant-specific image support
    private final Long variantId;

    private final String imageUrl;
    private final String publicId;
    private final Boolean primary;
    private final Integer displayOrder;
    private final LocalDateTime createdAt;

    // ==========================================
    // NEW CONSTRUCTOR WITH VARIANT ID
    // ==========================================

    public ProductImageResponse(
            Long id,
            Long productId,
            Long variantId,
            String imageUrl,
            String publicId,
            Boolean primary,
            Integer displayOrder,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.productId = productId;
        this.variantId = variantId;
        this.imageUrl = imageUrl;
        this.publicId = publicId;
        this.primary = primary;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
    }

    // ==========================================
    // OLD CONSTRUCTOR - BACKWARD COMPATIBILITY
    // ==========================================

    public ProductImageResponse(
            Long id,
            Long productId,
            String imageUrl,
            String publicId,
            Boolean primary,
            Integer displayOrder,
            LocalDateTime createdAt
    ) {
        this(
                id,
                productId,
                null,
                imageUrl,
                publicId,
                primary,
                displayOrder,
                createdAt
        );
    }

    // ==========================================
    // GETTERS
    // ==========================================

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    // NEW
    public Long getVariantId() {
        return variantId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPublicId() {
        return publicId;
    }

    public Boolean getPrimary() {
        return primary;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}