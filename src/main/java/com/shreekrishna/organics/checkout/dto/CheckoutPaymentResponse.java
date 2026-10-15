package com.shreekrishna.organics.checkout.dto;

import java.math.BigDecimal;

public record CheckoutPaymentResponse(
        String sessionId,
        String status,
        String razorpayOrderId,
        BigDecimal amount,
        String currency,
        Long finalizedOrderId
) {}
