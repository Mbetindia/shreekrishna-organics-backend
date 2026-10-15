package com.shreekrishna.organics.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PurchaseDisplayRequest(
        @NotNull @Min(0) Integer purchaseCount,
        @NotNull Boolean showPurchaseCount
) {}
