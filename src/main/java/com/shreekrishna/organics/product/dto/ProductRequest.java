package com.shreekrishna.organics.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductRequest {

    @NotNull(message = "Category ID is required")
    private Long categoryId;

    @NotBlank(message = "Product name is required")
    @Size(max = 200, message = "Product name must not exceed 200 characters")
    private String name;

    @NotBlank(message = "Slug is required")
    @Size(max = 220, message = "Slug must not exceed 220 characters")
    private String slug;

    @Size(max = 500, message = "Short description must not exceed 500 characters")
    private String shortDescription;

    private String description;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Base price cannot be negative")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "MRP cannot be negative")
    private BigDecimal mrp;

    // Optional during frontend migration; ProductService should preserve existing
    // values on update and use entity defaults when creating a product.
    @Size(max = 20, message = "HSN code must not exceed 20 characters")
    private String hsnCode;

    @DecimalMin(value = "0.00", message = "GST rate cannot be negative")
    @DecimalMax(value = "100.00", message = "GST rate cannot exceed 100%")
    private BigDecimal gstRate;

    private Boolean priceIncludesGst;

    private Boolean active;
    private Boolean featured;
    private Integer displayOrder;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }

    public Boolean getPriceIncludesGst() { return priceIncludesGst; }
    public void setPriceIncludesGst(Boolean priceIncludesGst) { this.priceIncludesGst = priceIncludesGst; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getFeatured() { return featured; }
    public void setFeatured(Boolean featured) { this.featured = featured; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
}
