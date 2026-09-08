package com.adrifit.backend.diet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DietResponse(
        Long id,
        String name,
        String description,
        String objective,
        Boolean active,
        Long trainerId,
        List<DietDayResponse> days,
        BigDecimal totalCalories,
        BigDecimal totalProtein,
        BigDecimal totalCarbs,
        BigDecimal totalFat,
        Integer dayCount,
        Instant createdAt,
        Instant updatedAt
) {}
