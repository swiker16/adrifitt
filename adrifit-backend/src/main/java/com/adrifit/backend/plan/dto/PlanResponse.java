package com.adrifit.backend.plan.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PlanResponse(
        Long id,
        String name,
        String description,
        BigDecimal monthlyPrice,
        Integer reviewFrequencyDays,
        boolean messagingEnabled,
        boolean analyticsEnabled,
        boolean pdfExportEnabled,
        boolean prioritySupport,
        boolean active,
        Instant createdAt
) {
}
