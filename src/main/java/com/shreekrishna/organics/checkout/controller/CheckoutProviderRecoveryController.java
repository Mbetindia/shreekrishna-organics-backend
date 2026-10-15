package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutProviderRecoveryResponse;
import com.shreekrishna.organics.checkout.service.CheckoutProviderRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutProviderRecoveryController {
    private final CheckoutProviderRecoveryService recovery;

    public CheckoutProviderRecoveryController(CheckoutProviderRecoveryService recovery) {
        this.recovery = recovery;
    }

    @GetMapping("/{id}/provider-recovery-preview")
    public ResponseEntity<CheckoutProviderRecoveryResponse> preview(
            @PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(recovery.preview(authentication.getName(), id));
    }

    @PostMapping("/{id}/recover-provider-order")
    public ResponseEntity<CheckoutProviderRecoveryResponse> recover(
            @PathVariable String id, Authentication authentication) {
        return ResponseEntity.ok(recovery.recover(authentication.getName(), id));
    }
}
