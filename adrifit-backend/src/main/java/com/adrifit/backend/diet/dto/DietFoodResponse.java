package com.adrifit.backend.diet.dto;

import java.math.BigDecimal;
import java.util.List;

public record DietFoodResponse(
        Long id,
        String foodName,
        BigDecimal quantity,
        String unit,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        String notes,
        Integer orderIndex,
        List<DietAlternativeResponse> alternatives
) {}
