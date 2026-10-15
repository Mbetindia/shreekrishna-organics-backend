package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutCaptureRecoveryResponse;
import com.shreekrishna.organics.checkout.service.CheckoutCaptureRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutCaptureRecoveryController {
    private final CheckoutCaptureRecoveryService service;

    public CheckoutCaptureRecoveryController(CheckoutCaptureRecoveryService service) {
        this.service = service;
    }

    @GetMapping("/{id}/capture-recovery-preview")
    public CheckoutCaptureRecoveryResponse preview(@PathVariable String id, Authentication auth) {
        return service.preview(auth.getName(), id);
    }

    @PostMapping("/{id}/recover-captured-payment")
    public ResponseEntity<CheckoutCaptureRecoveryResponse> recover(
            @PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(service.recover(auth.getName(), id));
    }
}
