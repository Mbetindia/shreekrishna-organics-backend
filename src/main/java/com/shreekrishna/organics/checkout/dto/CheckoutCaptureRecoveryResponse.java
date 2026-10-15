package com.shreekrishna.organics.checkout.dto;

public record CheckoutCaptureRecoveryResponse(
        String sessionId,
        String assessment,
        String sessionStatus,
        Long finalizedOrderId,
        String message
) {}
