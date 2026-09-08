package com.adrifit.backend.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

public record UpdateClientRequest(
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        String phone,

        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        String objective,

        String notes
) {
}
