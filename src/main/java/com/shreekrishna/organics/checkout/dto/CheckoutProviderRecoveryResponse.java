package com.shreekrishna.organics.checkout.dto;

public record CheckoutProviderRecoveryResponse(
        String sessionId,
        String sessionStatus,
        String assessment,
        String razorpayOrderId,
        int exactMatches,
        String message
) {}
