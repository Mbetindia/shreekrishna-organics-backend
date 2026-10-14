package com.shreekrishna.organics.checkout.dto;

public record CheckoutVerifyResponse(String sessionId, String status, Long orderId) {}
