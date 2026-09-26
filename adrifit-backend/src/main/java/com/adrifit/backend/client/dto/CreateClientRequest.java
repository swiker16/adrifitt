package com.adrifit.backend.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;

public record CreateClientRequest(
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Phone is required")
        String phone,

        @NotNull(message = "Birth date is required")
        @Past(message = "Birth date must be in the past")
        LocalDate birthDate,

        @NotBlank(message = "Objective is required")
        String objective,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotNull(message = "Plan id is required")
        Long planId,

        /* Optional: defaults to the authenticated trainer. */
        Long trainerId,

        /* Optional: defaults to MONTHLY. */
        com.adrifit.backend.plan.domain.BillingPeriod billingPeriod,

        /* Optional special conditions: price per billing period instead of the plan price. */
        @jakarta.validation.constraints.DecimalMin(value = "0.0", message = "El precio especial no puede ser negativo")
        java.math.BigDecimal customPrice,

        @jakarta.validation.constraints.Size(max = 200)
        String customPriceNote,

        String notes
) {
}
