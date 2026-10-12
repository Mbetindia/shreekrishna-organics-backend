package com.shreekrishna.organics.order.dto;

import com.shreekrishna.organics.order.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(
        @NotNull Long addressId,
        @NotNull PaymentMethod paymentMethod
) {}