package com.shreekrishna.organics.checkout.dto;

public record CheckoutReconciliationPreview(
        String sessionId,
        String sessionStatus,
        String razorpayOrderId,
        String assessment,
        int attemptsObserved,
        String capturedPaymentId,
        Long finalizedOrderId,
        String guidance
) {}
