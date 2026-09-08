package com.adrifit.backend.diet.dto;

import java.math.BigDecimal;
import java.util.List;

public record DietMealResponse(
        Long id,
        String name,
        String time,
        String notes,
        Integer orderIndex,
        List<DietFoodResponse> foods,
        BigDecimal totalCalories,
        BigDecimal totalProtein,
        BigDecimal totalCarbs,
        BigDecimal totalFat
) {}
