package com.shreekrishna.organics.payment.service;

public class DuplicateWebhookEventException
        extends RuntimeException {

    public DuplicateWebhookEventException(String message) {
        super(message);
    }

    public DuplicateWebhookEventException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}