package com.shreekrishna.organics.user.dto;

import com.shreekrishna.organics.user.entity.Role;

public record UserProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String mobile,
        Role role
) {
}