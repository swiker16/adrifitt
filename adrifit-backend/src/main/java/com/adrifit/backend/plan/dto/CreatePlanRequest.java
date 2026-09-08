package com.adrifit.backend.plan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePlanRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotNull(message = "Monthly price is required")
        @PositiveOrZero(message = "Monthly price must be zero or positive")
        BigDecimal monthlyPrice,

        @NotNull(message = "Review frequency is required")
        @Min(value = 1, message = "Review frequency must be at least 1 day")
        Integer reviewFrequencyDays,

        boolean messagingEnabled,
        boolean analyticsEnabled,
        boolean pdfExportEnabled,
        boolean prioritySupport
) {
}
