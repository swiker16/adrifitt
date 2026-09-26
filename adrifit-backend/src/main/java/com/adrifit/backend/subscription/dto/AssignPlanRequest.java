package com.adrifit.backend.subscription.dto;

import com.adrifit.backend.plan.domain.BillingPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Trainer assigns a plan. {@code customPrice} (optional) sets special conditions: the amount
 * charged every billing period instead of the plan price.
 */
public record AssignPlanRequest(
        @NotNull(message = "Plan id is required")
        Long planId,

        BillingPeriod billingPeriod,

        @DecimalMin(value = "0.0", message = "El precio especial no puede ser negativo")
        BigDecimal customPrice,

        @Size(max = 200)
        String customPriceNote
) {

    public AssignPlanRequest(Long planId) {
        this(planId, null, null, null);
    }
}
