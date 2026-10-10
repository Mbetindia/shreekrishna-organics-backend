package com.shreekrishna.organics.order.dto;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(@NotNull Long addressId) {}
