package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutSwitchToCodResponse;
import com.shreekrishna.organics.checkout.service.CheckoutSwitchToCodService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutSwitchToCodController {
    private final CheckoutSwitchToCodService service;

    public CheckoutSwitchToCodController(CheckoutSwitchToCodService service) {
        this.service = service;
    }

    @PostMapping("/{id}/switch-to-cod")
    public ResponseEntity<CheckoutSwitchToCodResponse> switchToCod(
            @PathVariable String id,
            Authentication authentication) {
        return ResponseEntity.ok(service.switchToCod(authentication.getName(), id));
    }
}
