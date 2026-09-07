package com.bienvenueblainville.auth.dto;

import com.bienvenueblainville.common.Role;

public record AuthResponse(String token, Long userId, String email, Role role) {
}
