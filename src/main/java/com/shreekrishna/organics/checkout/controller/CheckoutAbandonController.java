package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutAbandonResponse;
import com.shreekrishna.organics.checkout.service.CheckoutAbandonService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutAbandonController {
    private final CheckoutAbandonService service;

    public CheckoutAbandonController(CheckoutAbandonService service) {
        this.service = service;
    }

    @PostMapping("/{id}/abandon")
    public CheckoutAbandonResponse abandon(Authentication auth, @PathVariable String id) {
        return service.abandonUnstartedDraft(auth.getName(), id);
    }
}
