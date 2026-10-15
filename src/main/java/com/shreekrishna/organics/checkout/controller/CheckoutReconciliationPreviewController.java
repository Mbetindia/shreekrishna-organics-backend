package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutReconciliationPreview;
import com.shreekrishna.organics.checkout.service.CheckoutReconciliationPreviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutReconciliationPreviewController {
    private final CheckoutReconciliationPreviewService service;

    public CheckoutReconciliationPreviewController(CheckoutReconciliationPreviewService service) {
        this.service = service;
    }

    @GetMapping("/{id}/reconcile-preview")
    public CheckoutReconciliationPreview reconcile(@PathVariable String id, Authentication auth) {
        return service.inspect(auth.getName(), id);
    }
}
