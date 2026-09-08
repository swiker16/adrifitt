package com.adrifit.backend.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateWorkoutRequest(
        @NotBlank String name,
        String description,
        String objective,
        Integer daysPerWeek,
        @Valid List<WorkoutExerciseRequest> exercises
) {}
