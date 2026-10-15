package com.shreekrishna.organics.checkout.dto;

public record CheckoutSwitchToCodResponse(
        String sessionId,
        String assessment,
        String sessionStatus,
        Long orderId,
        boolean switchedToCod,
        String message
) {}
