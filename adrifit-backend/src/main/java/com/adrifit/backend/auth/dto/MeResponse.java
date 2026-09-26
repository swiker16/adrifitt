package com.adrifit.backend.auth.dto;

import com.adrifit.backend.user.domain.Role;

public record MeResponse(
        Long userId,
        String username,
        String email,
        Role role,
        boolean mustChangePassword,
        Long clientId,
        String firstName,
        String lastName,
        /* Registered passkeys: the app offers to create one when 0. */
        long passkeys
) {
}
