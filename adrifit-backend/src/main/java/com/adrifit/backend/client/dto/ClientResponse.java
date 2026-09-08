package com.adrifit.backend.client.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ClientResponse(
        Long id,
        Long userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDate birthDate,
        String objective,
        String notes,
        Long trainerId,
        String photoBase64,
        Instant createdAt
) {
}
