package com.adrifit.backend.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record AssignPlanRequest(
        @NotNull(message = "Plan id is required")
        Long planId
) {
}
