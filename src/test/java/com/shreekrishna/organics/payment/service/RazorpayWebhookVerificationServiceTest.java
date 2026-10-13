package com.shreekrishna.organics.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

class RazorpayWebhookVerificationServiceTest {

    private RazorpayWebhookVerificationService service;

    private static final String TEST_SECRET = "dummy_webhook_secret_for_unit_tests_only";

    @BeforeEach
    void setUp() {
        service = new RazorpayWebhookVerificationService();

        ReflectionTestUtils.setField(
                service,
                "webhookSecret",
                TEST_SECRET
        );
    }

    private String generateSignature(String payload) throws Exception {

        Mac mac = Mac.getInstance("HmacSHA256");

        mac.init(new SecretKeySpec(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        ));

        byte[] hash = mac.doFinal(
                payload.getBytes(StandardCharsets.UTF_8)
        );

        return HexFormat.of().formatHex(hash);
    }

    @Test
    void shouldAcceptValidSignature() throws Exception {

        String payload = "{\"event\":\"payment.captured\"}";

        String signature = generateSignature(payload);

        assertTrue(service.verify(payload, signature));
    }

    @Test
    void shouldRejectInvalidSignature() {

        String payload = "{\"event\":\"payment.captured\"}";

        assertFalse(service.verify(payload, "invalid_signature"));
    }

    @Test
    void shouldRejectMissingSignature() {

        String payload = "{\"event\":\"payment.captured\"}";

        assertFalse(service.verify(payload, null));
        assertFalse(service.verify(payload, ""));
    }

    @Test
    void shouldRejectModifiedPayload() throws Exception {

        String originalPayload = "{\"event\":\"payment.captured\"}";
        String modifiedPayload = "{\"event\":\"payment.failed\"}";

        String signature = generateSignature(originalPayload);

        assertFalse(service.verify(modifiedPayload, signature));
    }

    @Test
    void shouldRejectMissingWebhookSecret() throws Exception {

        String payload = "{\"event\":\"payment.captured\"}";
        String signature = generateSignature(payload);

        ReflectionTestUtils.setField(
                service,
                "webhookSecret",
                ""
        );

        assertFalse(service.verify(payload, signature));
    }
}