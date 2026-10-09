package com.shreekrishna.organics.cart.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        Long variantId,
        BigDecimal sizeValue,
        String sizeUnit,
        BigDecimal price,
        BigDecimal mrp,
        Integer quantity,
        BigDecimal lineTotal,
        Integer availableStock,
        Boolean available
) {}