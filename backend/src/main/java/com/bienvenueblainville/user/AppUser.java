package com.bienvenueblainville.user;

import com.bienvenueblainville.common.Role;

import java.time.Instant;

public record AppUser(
        Long id,
        String email,
        String passwordHash,
        Role role,
        Instant createdAt,
        Instant updatedAt
) {
}
