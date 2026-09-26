package com.adrifit.backend.subscription.dto;

import com.adrifit.backend.plan.domain.BillingPeriod;
import jakarta.validation.constraints.NotNull;

/** Client self-service plan / billing period change. */
public record ChangePlanRequest(
        @NotNull(message = "Plan id is required")
        Long planId,

        BillingPeriod billingPeriod
) {
}
