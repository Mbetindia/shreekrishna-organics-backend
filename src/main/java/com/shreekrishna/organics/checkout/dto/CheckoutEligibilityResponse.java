package com.shreekrishna.organics.checkout.dto;

/** Advisory UI state. The actual order/payment endpoints MUST recheck eligibility under locks. */
public record CheckoutEligibilityResponse(
        boolean canShowCheckout,
        boolean canAttemptCOD,
        boolean canStartNewOnlineCheckout,
        boolean unresolvedPayment,
        String blockingSessionId,
        String assessment,
        String message
) {}
