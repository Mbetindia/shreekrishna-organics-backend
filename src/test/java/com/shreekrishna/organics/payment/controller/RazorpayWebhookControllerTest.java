package com.shreekrishna.organics.payment.controller;

import com.shreekrishna.organics.payment.service.RazorpayWebhookVerificationService;
import com.shreekrishna.organics.payment.service.RazorpayWebhookProcessingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.shreekrishna.organics.payment.service.DuplicateWebhookEventException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RazorpayWebhookControllerTest {

    private RazorpayWebhookVerificationService verificationService;
    private RazorpayWebhookProcessingService processingService;
    private RazorpayWebhookController controller;

    @BeforeEach
    void setUp() {

        verificationService =
                mock(RazorpayWebhookVerificationService.class);

        processingService =
                mock(RazorpayWebhookProcessingService.class);

        controller = new RazorpayWebhookController(
                verificationService,
                processingService
        );
    }

    @Test
    void shouldRejectInvalidSignature() {

        String payload = "{\"event\":\"payment.captured\"}";

        when(verificationService.verify(payload, "wrong_signature"))
                .thenReturn(false);

        ResponseEntity<String> response =
                controller.handleWebhook(
                        payload,
                        "wrong_signature",
                        "evt_123"
                );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                response.getStatusCode()
        );

        assertEquals(
                "Invalid webhook signature",
                response.getBody()
        );

        verifyNoInteractions(processingService);
    }

    @Test
    void shouldRejectMissingEventId() {

        String payload = "{\"event\":\"payment.captured\"}";

        when(verificationService.verify(payload, "valid_signature"))
                .thenReturn(true);

        ResponseEntity<String> response =
                controller.handleWebhook(
                        payload,
                        "valid_signature",
                        null
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        verifyNoInteractions(processingService);
    }

    @Test
    void shouldRejectBlankEventId() {

        String payload = "{\"event\":\"payment.captured\"}";

        when(verificationService.verify(payload, "valid_signature"))
                .thenReturn(true);

        ResponseEntity<String> response =
                controller.handleWebhook(
                        payload,
                        "valid_signature",
                        "   "
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        verifyNoInteractions(processingService);
    }

    @Test
    void shouldRejectEventIdLongerThan150Characters() {

        String payload = "{\"event\":\"payment.captured\"}";

        when(verificationService.verify(payload, "valid_signature"))
                .thenReturn(true);

        ResponseEntity<String> response =
                controller.handleWebhook(
                        payload,
                        "valid_signature",
                        "a".repeat(151)
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        verifyNoInteractions(processingService);
    }


    @Test
    void shouldProcessWebhookWhenSignatureAndEventIdAreValid() {

        String payload = "{\"event\":\"payment.captured\"}";
        String signature = "valid_signature";
        String eventId = "evt_valid_123";

        when(verificationService.verify(payload, signature))
                .thenReturn(true);

        ResponseEntity<String> response =
                controller.handleWebhook(
                        payload,
                        signature,
                        eventId
                );

        // Valid webhook should return HTTP 200
        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertEquals(
                "Webhook processed",
                response.getBody()
        );

        // Verify signature was checked
        verify(verificationService, times(1))
                .verify(payload, signature);

        // Verify webhook processing service was called exactly once
        verify(processingService, times(1))
                .process(payload, eventId);

        verifyNoMoreInteractions(processingService);
    }

    @Test
    void shouldReturn503ForDuplicateWebhookEvent() {

        DuplicateWebhookEventException exception =
                new DuplicateWebhookEventException(
                        "Duplicate webhook event",
                        new IllegalStateException("Duplicate event ID")
                );

        ResponseEntity<String> response =
                controller.handleDuplicateWebhook(exception);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertEquals(
                "Webhook event conflict. Retry later.",
                response.getBody()
        );

        verifyNoInteractions(processingService);
    }

    @Test
    void shouldReturn409ForWebhookProcessingError() {

        RuntimeException exception =
                new IllegalStateException("Payment mismatch");

        ResponseEntity<String> response =
                controller.handleInvalidWebhook(exception);

        assertEquals(
                HttpStatus.CONFLICT,
                response.getStatusCode()
        );

        assertEquals(
                "Webhook data conflicts with payment state.",
                response.getBody()
        );
    }
}