package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutCancelResponse;
import com.shreekrishna.organics.checkout.service.CheckoutCancelService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutCancelController {
    private final CheckoutCancelService service;

    public CheckoutCancelController(CheckoutCancelService service) {
        this.service = service;
    }

    @PostMapping("/{id}/cancel")
    public CheckoutCancelResponse cancel(Authentication auth, @PathVariable String id) {
        return service.cancelCheckout(auth.getName(), id);
    }
}
