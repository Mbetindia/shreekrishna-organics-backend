
package com.shreekrishna.organics.auth.controller;

import com.shreekrishna.organics.auth.dto.AuthResponse;
import com.shreekrishna.organics.auth.dto.LoginRequest;
import com.shreekrishna.organics.auth.dto.RegisterRequest;
import com.shreekrishna.organics.auth.dto.RegisterResponse;
import com.shreekrishna.organics.auth.dto.OtpVerifyRequest;

import com.shreekrishna.organics.auth.service.AuthService;
import com.shreekrishna.organics.auth.service.CustomerOtpAuthService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CustomerOtpAuthService customerOtpAuthService;

    public AuthController(
            AuthService authService,
            CustomerOtpAuthService customerOtpAuthService
    ) {
        this.authService = authService;
        this.customerOtpAuthService = customerOtpAuthService;
    }

    // Existing Registration
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Existing Email + Password Login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    // Customer Mobile OTP Login
    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request
    ) {
        return ResponseEntity.ok(
                customerOtpAuthService.loginWithOtp(
                        request.accessToken()
                )
        );
    }
}
