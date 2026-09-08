package com.adrifit.backend.diet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record DietFoodRequest(
        Long id,
        @NotBlank @Size(max = 200) String foodName,
        BigDecimal quantity,
        @Size(max = 50) String unit,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbsGrams,
        BigDecimal fatGrams,
        @Size(max = 500) String notes,
        Integer orderIndex,
        List<DietAlternativeRequest> alternatives
) {}
