package com.shreekrishna.organics.order.controller;

import com.shreekrishna.organics.order.dto.*;
import com.shreekrishna.organics.order.service.CustomerOrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CustomerOrderController {
    private final CustomerOrderService service;

    public CustomerOrderController(CustomerOrderService service) { this.service = service; }

    @GetMapping("/checkout/summary")
    public CheckoutSummaryResponse summary(Authentication auth) {
        return service.summary(auth.getName());
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(Authentication auth, @Valid @RequestBody PlaceOrderRequest request) {
        return service.place(auth.getName(), request);
    }

    @GetMapping("/orders")
    public List<OrderResponse> list(Authentication auth) { return service.list(auth.getName()); }

    @GetMapping("/orders/{id}")
    public OrderResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(auth.getName(), id);
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancel(Authentication auth, @PathVariable Long id) {
        return service.cancel(auth.getName(), id);
    }
}
