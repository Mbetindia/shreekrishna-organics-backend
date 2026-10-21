package com.shreekrishna.organics.checkout.controller;

import com.shreekrishna.organics.checkout.dto.CheckoutSwitchToCodResponse;
import com.shreekrishna.organics.checkout.service.CheckoutSwitchToCodService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout-sessions")
public class CheckoutBuyNowCodController {
    private final CheckoutSwitchToCodService service;

    public CheckoutBuyNowCodController(CheckoutSwitchToCodService service) {
        this.service = service;
    }

    @PostMapping("/{id}/buy-now-cod")
    public CheckoutSwitchToCodResponse place(Authentication auth, @PathVariable String id) {
        return service.placeBuyNowCod(auth.getName(), id);
    }
}
