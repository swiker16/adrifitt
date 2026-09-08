package com.adrifit.backend.diet.dto;

import java.math.BigDecimal;

public record DietAlternativeResponse(
        Long id,
        String alternativeName,
        BigDecimal quantity,
        String unit,
        String notes,
        Integer orderIndex
) {}
