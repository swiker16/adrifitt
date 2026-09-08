package com.adrifit.backend.auth.dto;

import com.adrifit.backend.user.domain.Role;

public record AuthResponse(
        String token,
        Long userId,
        String username,
        Role role
) {
}
