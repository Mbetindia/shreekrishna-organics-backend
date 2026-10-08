
package com.shreekrishna.organics.product.dto;

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
}
