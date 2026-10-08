package com.shreekrishna.organics.user.controller;

import com.shreekrishna.organics.user.dto.UpdateProfileRequest;
import com.shreekrishna.organics.user.dto.UserProfileResponse;
import com.shreekrishna.organics.user.service.UserService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(
            Authentication authentication) {

        UserProfileResponse response =
                userService.getProfile(authentication.getName());

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileResponse response =
                userService.updateProfile(
                        authentication.getName(),
                        request
                );

        return ResponseEntity.ok(response);
    }
}