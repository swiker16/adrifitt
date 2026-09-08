package com.adrifit.backend.workout.dto;

public record WorkoutExerciseResponse(
        Long id,
        String exerciseName,
        Integer sets,
        Integer reps,
        Integer rir,
        Integer restSeconds,
        String notes,
        Integer orderIndex,
        Integer dayNumber,
        String dayName,
        String warmUpSets,
        String approxReps
) {}
