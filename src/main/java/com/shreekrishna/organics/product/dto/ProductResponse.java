package com.shreekrishna.organics.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductResponse {

    private Long id;

    private Long categoryId;
    private String categoryName;

    private String name;
    private String slug;

    private String shortDescription;
    private String description;

    private BigDecimal basePrice;
    private BigDecimal mrp;

    private Boolean active;
    private Boolean featured;

    private Integer displayOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ProductResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String name,
            String slug,
            String shortDescription,
            String description,
            BigDecimal basePrice,
            BigDecimal mrp,
            Boolean active,
            Boolean featured,
            Integer displayOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.name = name;
        this.slug = slug;
        this.shortDescription = shortDescription;
        this.description = description;
        this.basePrice = basePrice;
        this.mrp = mrp;
        this.active = active;
        this.featured = featured;
        this.displayOrder = displayOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getFeatured() {
        return featured;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}