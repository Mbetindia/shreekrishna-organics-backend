package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutRecoveryResponse;
import com.shreekrishna.organics.checkout.service.CheckoutRecoveryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutRecoveryController {
    private final CheckoutRecoveryService service;

    public CheckoutRecoveryController(CheckoutRecoveryService service) {
        this.service = service;
    }

    @GetMapping("/{id}/recovery")
    public CheckoutRecoveryResponse recover(@PathVariable String id, Authentication auth) {
        return service.inspect(auth.getName(), id);
    }
}
