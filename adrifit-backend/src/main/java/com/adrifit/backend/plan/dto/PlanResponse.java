package com.adrifit.backend.plan.dto;

import com.adrifit.backend.plan.domain.BillingPeriod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PlanResponse(
        Long id,
        String name,
        String description,
        BigDecimal monthlyPrice,
        BigDecimal quarterlyPrice,
        BigDecimal semiannualPrice,
        BigDecimal annualPrice,
        /* Offered billing periods with the per-month equivalent and saving vs. paying monthly. */
        List<PeriodPrice> prices,
        List<String> features,
        Integer reviewFrequencyDays,
        boolean messagingEnabled,
        boolean analyticsEnabled,
        boolean pdfExportEnabled,
        boolean prioritySupport,
        boolean active,
        Instant createdAt
) {

    public record PeriodPrice(
            BillingPeriod period,
            int months,
            BigDecimal price,
            BigDecimal monthlyEquivalent,
            int savingPercent
    ) {
    }
}
