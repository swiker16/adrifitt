package com.adrifit.backend.diet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DietDayRequest(
        Long id,
        @NotBlank @Size(max = 200) String name,
        Integer dayNumber,
        @Size(max = 1000) String notes,
        Integer orderIndex,
        List<DietMealRequest> meals
) {}
