package com.shreekrishna.organics.user.dto;

import java.time.LocalDateTime;

public record AdminCustomerResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String mobile,
        boolean enabled,
        LocalDateTime createdAt
) {}
