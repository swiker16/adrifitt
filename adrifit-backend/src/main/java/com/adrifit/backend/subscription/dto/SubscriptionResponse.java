package com.adrifit.backend.subscription.dto;

import com.adrifit.backend.plan.dto.PlanResponse;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.time.LocalDate;

public record SubscriptionResponse(
        Long id,
        Long clientId,
        Long planId,
        String planName,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate renewalDate,
        SubscriptionStatus status,
        boolean active,
        boolean cancelAtPeriodEnd,
        com.adrifit.backend.plan.domain.BillingPeriod billingPeriod,
        java.math.BigDecimal customPrice,
        String customPriceNote,
        /* Amount charged each billing period (custom price if any). */
        java.math.BigDecimal effectivePrice,
        java.math.BigDecimal monthlyEquivalent,
        Instant cancelledAt,
        PlanResponse plan
) {
}
