package com.adrifit.backend.diet.dto;

import java.math.BigDecimal;
import java.util.List;

public record DietDayResponse(
        Long id,
        String name,
        Integer dayNumber,
        String notes,
        Integer orderIndex,
        List<DietMealResponse> meals,
        BigDecimal totalCalories,
        BigDecimal totalProtein,
        BigDecimal totalCarbs,
        BigDecimal totalFat,
        Integer mealCount
) {}
