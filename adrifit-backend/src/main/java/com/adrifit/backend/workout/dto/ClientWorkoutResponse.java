package com.adrifit.backend.workout.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ClientWorkoutResponse(
        Long id,
        Long clientId,
        WorkoutResponse workout,
        Instant assignedAt,
        LocalDate endDate,
        boolean active
) {}
