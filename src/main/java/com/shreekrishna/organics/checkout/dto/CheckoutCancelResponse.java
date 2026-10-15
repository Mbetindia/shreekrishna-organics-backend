package com.shreekrishna.organics.checkout.dto;

public record CheckoutCancelResponse(
        String sessionId,
        String status,
        String assessment,
        boolean cancelled,
        Long finalizedOrderId,
        String message
) {}
