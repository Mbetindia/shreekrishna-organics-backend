
package com.shreekrishna.organics.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ProductVariantRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    @NotNull(message = "Size value is required")
    @DecimalMin(value = "0.01")
    private BigDecimal sizeValue;

    @NotBlank(message = "Size unit is required")
    @Size(max = 20)
    private String sizeUnit;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    @DecimalMin(value = "0.00")
    private BigDecimal mrp;

    private Boolean active = true;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getSizeValue() {
        return sizeValue;
    }

    public void setSizeValue(BigDecimal sizeValue) {
        this.sizeValue = sizeValue;
    }

    public String getSizeUnit() {
        return sizeUnit;
    }

    public void setSizeUnit(String sizeUnit) {
        this.sizeUnit = sizeUnit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getMrp() {
        return mrp;
    }

    public void setMrp(BigDecimal mrp) {
        this.mrp = mrp;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
