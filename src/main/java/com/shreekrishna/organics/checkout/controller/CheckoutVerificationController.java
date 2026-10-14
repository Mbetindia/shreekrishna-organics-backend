package com.shreekrishna.organics.checkout.controller;

import com.razorpay.RazorpayException;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyRequest;
import com.shreekrishna.organics.checkout.dto.CheckoutVerifyResponse;
import com.shreekrishna.organics.checkout.service.CheckoutPaymentFinalizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutVerificationController {
    private final CheckoutPaymentFinalizationService service;
    private final boolean enabled;

    public CheckoutVerificationController(CheckoutPaymentFinalizationService service,
             @Value("${checkout.online-payments-enabled:false}") boolean enabled) {
        this.service = service;
        this.enabled = enabled;
    }

    @PostMapping("/{id}/verify")
    public CheckoutVerifyResponse verify(@PathVariable String id,
                                        @RequestBody CheckoutVerifyRequest request,
                                        Authentication auth) throws RazorpayException {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Checkout payments disabled until webhook and recovery integration is installed");
        if (auth == null || !auth.isAuthenticated())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return service.verify(auth.getName(), id, request);
    }
}
