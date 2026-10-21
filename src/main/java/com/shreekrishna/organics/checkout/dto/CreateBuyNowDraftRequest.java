package com.shreekrishna.organics.checkout.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

public record CreateBuyNowDraftRequest(
        @NotNull Long addressId,
        @NotNull Long variantId,
        @NotNull @Min(1) @Max(100) Integer quantity
) {}
