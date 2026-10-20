package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutDraftResponse;
import com.shreekrishna.organics.checkout.dto.CreateCheckoutDraftRequest;
import com.shreekrishna.organics.checkout.dto.CreateBuyNowDraftRequest;
import com.shreekrishna.organics.checkout.service.CheckoutDraftService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutDraftController {
    private final CheckoutDraftService service;

    public CheckoutDraftController(CheckoutDraftService service) {
        this.service = service;
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutDraftResponse create(Authentication auth,
                                        @Valid @RequestBody CreateCheckoutDraftRequest request) {
        return service.create(auth.getName(), request.addressId());
    }

    @PostMapping("/buy-now/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutDraftResponse createBuyNow(Authentication auth,
            @Valid @RequestBody CreateBuyNowDraftRequest request) {
        return service.createBuyNow(auth.getName(), request.addressId(),
                request.variantId(), request.quantity());
    }

    @GetMapping("/{id}")
    public CheckoutDraftResponse get(Authentication auth, @PathVariable String id) {
        return service.get(auth.getName(), id);
    }
}
