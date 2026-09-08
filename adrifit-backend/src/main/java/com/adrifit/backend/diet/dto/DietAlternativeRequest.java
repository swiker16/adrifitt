package com.adrifit.backend.diet.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DietAlternativeRequest(
        Long id,
        @Size(max = 200) String alternativeName,
        BigDecimal quantity,
        @Size(max = 50) String unit,
        @Size(max = 500) String notes,
        Integer orderIndex
) {}
