package com.adrifit.backend.diet.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DietSummaryResponse(
        Long id,
        String name,
        String description,
        String objective,
        Boolean active,
        Integer dayCount,
        BigDecimal avgCalories,
        Instant createdAt,
        Instant updatedAt
) {}
