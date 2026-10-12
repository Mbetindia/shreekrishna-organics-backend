package com.shreekrishna.organics.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class RazorpayWebhookVerificationService {

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    public boolean verify(String payload, String signature) {

        if (webhookSecret == null || webhookSecret.isBlank()
                || payload == null
                || signature == null || signature.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            byte[] expected = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            byte[] received = HexFormat.of().parseHex(signature.trim());

            return MessageDigest.isEqual(expected, received);

        } catch (Exception e) {
            return false;
        }
    }
}