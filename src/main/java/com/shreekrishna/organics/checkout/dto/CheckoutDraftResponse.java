package com.shreekrishna.organics.checkout.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CheckoutDraftResponse(
        String id,
        String status,
        Long addressId,
        BigDecimal subtotal,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime expiresAt,
        List<Item> items
) {
    public record Item(Long variantId, String productName, String sku,
                       Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
