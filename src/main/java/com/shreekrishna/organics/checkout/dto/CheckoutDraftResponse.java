package com.shreekrishna.organics.checkout.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutDraftResponse(
        String id,
        String status,
        Long addressId,
        BigDecimal subtotal,
        BigDecimal totalAmount,
        String currency,
        String expiresAt,
        List<Item> items,
        BigDecimal deliveryCharge,
        BigDecimal taxAmount,
        BigDecimal includedTaxAmount,
        BigDecimal additionalTax
) {
    public record Item(Long variantId, String productName, String sku,
                       Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                       BigDecimal gstRate, Boolean priceIncludesGst, BigDecimal taxAmount) {}
}
