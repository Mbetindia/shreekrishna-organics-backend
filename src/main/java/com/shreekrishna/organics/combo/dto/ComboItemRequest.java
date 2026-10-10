package com.shreekrishna.organics.combo.dto;

public class ComboItemRequest {

    private Long productVariantId;

    private Integer quantity;

    public ComboItemRequest() {
    }

    public Long getProductVariantId() {
        return productVariantId;
    }

    public void setProductVariantId(Long productVariantId) {
        this.productVariantId = productVariantId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}