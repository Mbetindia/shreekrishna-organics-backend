package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutEligibilityResponse;
import com.shreekrishna.organics.checkout.service.CheckoutEligibilityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutEligibilityController {
    private final CheckoutEligibilityService service;

    public CheckoutEligibilityController(CheckoutEligibilityService service) { this.service = service; }

    @GetMapping("/eligibility")
    public CheckoutEligibilityResponse eligibility(Authentication auth) {
        return service.inspect(auth.getName());
    }
}
