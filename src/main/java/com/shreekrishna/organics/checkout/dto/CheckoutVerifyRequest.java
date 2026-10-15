package com.shreekrishna.organics.checkout.dto;

public record CheckoutVerifyRequest(
        String razorpayOrderId,
        String razorpayPaymentId,
        String razorpaySignature
) {}
