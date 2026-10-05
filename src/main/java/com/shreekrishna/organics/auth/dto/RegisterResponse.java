package com.shreekrishna.organics.auth.dto;

import com.shreekrishna.organics.user.entity.Role;

public record RegisterResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String mobile,
        Role role
) {
}