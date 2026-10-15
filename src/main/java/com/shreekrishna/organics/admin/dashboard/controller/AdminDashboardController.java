package com.shreekrishna.organics.admin.dashboard.controller;

import com.shreekrishna.organics.admin.dashboard.dto.AdminDashboardResponse;
import com.shreekrishna.organics.admin.dashboard.service.AdminDashboardService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    private void requireAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream()
                .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin access required"
            );
        }
    }

    @GetMapping
    public AdminDashboardResponse getDashboard(Authentication auth) {
        requireAdmin(auth);
        return service.getDashboardSummary();
    }
}