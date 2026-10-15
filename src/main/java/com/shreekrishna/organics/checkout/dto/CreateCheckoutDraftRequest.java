package com.shreekrishna.organics.checkout.dto;

import jakarta.validation.constraints.NotNull;

public record CreateCheckoutDraftRequest(@NotNull Long addressId) {}
