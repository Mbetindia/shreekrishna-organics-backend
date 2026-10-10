package com.shreekrishna.organics.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutSummaryResponse(List<CheckoutItem> items, BigDecimal subtotal,
                                      BigDecimal deliveryCharge, BigDecimal discountAmount,
                                      BigDecimal taxAmount, BigDecimal totalAmount) {
    public record CheckoutItem(Long variantId, String productName, String sku,
                               int quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                               boolean available) {}
}
