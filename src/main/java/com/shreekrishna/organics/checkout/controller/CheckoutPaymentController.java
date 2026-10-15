package com.shreekrishna.organics.checkout.controller;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.checkout.dto.CheckoutPaymentResponse;
import com.shreekrishna.organics.checkout.service.CheckoutPaymentStartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutPaymentController {
    private final CheckoutPaymentStartService paymentStart;

    public CheckoutPaymentController(CheckoutPaymentStartService paymentStart) {
        this.paymentStart = paymentStart;
    }

    @PostMapping("/{id}/start-payment")
    public ResponseEntity<CheckoutPaymentResponse> start(
            @PathVariable String id, Authentication authentication) throws RazorpayException {
        return ResponseEntity.ok(paymentStart.start(authentication.getName(), id));
    }

    @GetMapping("/{id}/payment-status")
    public ResponseEntity<CheckoutPaymentResponse> status(
            @PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(paymentStart.status(authentication.getName(), id));
    }
}
