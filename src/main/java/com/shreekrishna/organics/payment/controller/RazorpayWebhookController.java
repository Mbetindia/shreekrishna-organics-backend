package com.shreekrishna.organics.payment.controller;

import com.shreekrishna.organics.payment.service.RazorpayWebhookVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.shreekrishna.organics.payment.service.RazorpayWebhookProcessingService;
import com.shreekrishna.organics.payment.service.DuplicateWebhookEventException;

@RestController
@RequestMapping("/api/v1/payments/razorpay/webhook")
public class RazorpayWebhookController {

    private final RazorpayWebhookVerificationService verificationService;
    private final RazorpayWebhookProcessingService processingService;

    public RazorpayWebhookController(
            RazorpayWebhookVerificationService verificationService,
            RazorpayWebhookProcessingService processingService) {

        this.verificationService = verificationService;
        this.processingService = processingService;
    }
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(
                    value = "X-Razorpay-Signature",
                    required = false
            ) String signature,
            @RequestHeader(
                    value = "X-Razorpay-Event-Id",
                    required = false
            ) String eventId) {

        // Step 1: Verify Razorpay signature
        if (!verificationService.verify(payload, signature)) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid webhook signature");
        }

        // Step 2: Validate event ID
        if (eventId == null
                || eventId.isBlank()
                || eventId.length() > 150) {

            return ResponseEntity
                    .badRequest()
                    .body("Invalid Razorpay webhook event ID");
        }

        // Process verified Razorpay webhook
        processingService.process(payload, eventId);

        return ResponseEntity
                .ok("Webhook processed");
    }
    @ExceptionHandler(DuplicateWebhookEventException.class)
    public ResponseEntity<String> handleDuplicateWebhook(
            DuplicateWebhookEventException exception) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Webhook event conflict. Retry later.");
    }
    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<String> handleInvalidWebhook(
            RuntimeException exception) {

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("Webhook data conflicts with payment state.");
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<String> handleUnsupportedWebhook(
            UnsupportedOperationException exception) {

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Webhook processing unavailable. Retry later.");
    }
}