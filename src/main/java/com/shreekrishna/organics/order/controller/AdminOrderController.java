package com.shreekrishna.organics.order.controller;

import com.shreekrishna.organics.order.dto.*;
import com.shreekrishna.organics.order.service.AdminOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) { this.service = service; }

    // Explicit authority check: protects endpoints even if method security is not enabled.
    // Match this to the exact role authority granted by your JWT filter.
    private void requireAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @GetMapping
    public Page<OrderResponse> list(Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(auth);
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public OrderResponse get(Authentication auth, @PathVariable Long id) {
        requireAdmin(auth);
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    public OrderResponse status(Authentication auth, @PathVariable Long id,
            @Valid @RequestBody AdminOrderStatusRequest request) {
        requireAdmin(auth);
        return service.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Authentication auth, @PathVariable Long id) {
        requireAdmin(auth);
        return service.cancel(id);
    }

    @PatchMapping("/{id}/cod-payment")
    public OrderResponse codPayment(Authentication auth, @PathVariable Long id) {
        requireAdmin(auth);
        return service.recordCodPayment(id);
    }
}
