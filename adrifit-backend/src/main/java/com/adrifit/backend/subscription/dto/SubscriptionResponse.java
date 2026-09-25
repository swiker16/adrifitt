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
        Instant cancelledAt,
        PlanResponse plan
) {
}
