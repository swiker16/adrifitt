package com.adrifit.backend.workout.dto;

import java.time.Instant;
import java.util.List;

public record WorkoutResponse(
        Long id,
        String name,
        String description,
        String objective,
        Integer daysPerWeek,
        Instant createdAt,
        List<WorkoutExerciseResponse> exercises
) {}
