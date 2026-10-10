package com.shreekrishna.organics.order.dto;

import com.shreekrishna.organics.order.entity.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(Long id, Long variantId, String productName, String sku,
                                BigDecimal sizeValue, String sizeUnit, int quantity,
                                BigDecimal unitPrice, BigDecimal lineTotal) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getVariantId(), item.getProductName(),
                item.getSku(), item.getSizeValue(), item.getSizeUnit(), item.getQuantity(),
                item.getUnitPrice(), item.getLineTotal());
    }
}
