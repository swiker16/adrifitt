package com.adrifit.backend.diet.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ClientDietResponse(
        Long id,
        Long clientId,
        Long dietId,
        String dietName,
        String dietObjective,
        Instant assignedAt,
        LocalDate startDate,
        LocalDate endDate,
        Boolean active,
        String trainerNotes,
        DietResponse diet
) {}
