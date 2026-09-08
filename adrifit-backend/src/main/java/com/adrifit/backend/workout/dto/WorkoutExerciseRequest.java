package com.adrifit.backend.workout.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkoutExerciseRequest(
        Long id,
        @NotBlank String exerciseName,
        @NotNull @Min(1) Integer sets,
        Integer reps,
        Integer rir,
        Integer restSeconds,
        String notes,
        @NotNull @Min(0) Integer orderIndex,
        Integer dayNumber,
        String dayName,
        String warmUpSets,
        String approxReps
) {}
