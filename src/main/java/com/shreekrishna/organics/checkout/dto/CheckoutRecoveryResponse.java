package com.shreekrishna.organics.checkout.dto;

/** Presentation / safety hints only. These flags do not authorize order or payment creation. */
public record CheckoutRecoveryResponse(
        String sessionId,
        String sessionStatus,
        String assessment,
        String screen,
        boolean canShowAddressAndPaymentSelection,
        boolean canAbandonUnstartedDraft,
        boolean paymentOrCODSubmissionBlocked,
        Long finalizedOrderId,
        String message
) {}
