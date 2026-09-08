package com.adrifit.backend.diet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record DietMealRequest(
        Long id,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 50) String time,
        @Size(max = 1000) String notes,
        Integer orderIndex,
        List<DietFoodRequest> foods
) {}
