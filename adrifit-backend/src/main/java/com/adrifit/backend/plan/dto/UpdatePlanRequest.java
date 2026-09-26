package com.adrifit.backend.plan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdatePlanRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotNull(message = "El precio mensual es obligatorio")
        @PositiveOrZero(message = "El precio debe ser cero o positivo")
        BigDecimal monthlyPrice,

        @PositiveOrZero(message = "El precio debe ser cero o positivo")
        BigDecimal quarterlyPrice,

        @PositiveOrZero(message = "El precio debe ser cero o positivo")
        BigDecimal semiannualPrice,

        @PositiveOrZero(message = "El precio debe ser cero o positivo")
        BigDecimal annualPrice,

        @Size(max = 10000, message = "Demasiado texto en las características")
        String features,

        @NotNull(message = "Review frequency is required")
        @Min(value = 1, message = "Review frequency must be at least 1 day")
        Integer reviewFrequencyDays,

        boolean messagingEnabled,
        boolean analyticsEnabled,
        boolean pdfExportEnabled,
        boolean prioritySupport,
        boolean active
) {
}
