package com.shreekrishna.organics.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test")
public class SecurityTestController {

    @GetMapping("/protected")
    public String protectedEndpoint(
            Authentication authentication) {

        return "Authenticated user: "
                + authentication.getName();
    }
    @GetMapping("/admin")
    public String adminEndpoint() {
        return "Admin access successful";
    }
}