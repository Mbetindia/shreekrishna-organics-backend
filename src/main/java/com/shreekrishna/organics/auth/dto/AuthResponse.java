package com.shreekrishna.organics.auth.dto;

import com.shreekrishna.organics.user.entity.Role;

public record AuthResponse(

        String accessToken,
        String tokenType,
        long expiresIn,
        Long userId,
        String firstName,
        String lastName,
        String email,
        Role role

) {
}