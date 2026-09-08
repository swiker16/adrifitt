package com.adrifit.backend.workout.dto;

import jakarta.validation.constraints.NotNull;

public record AssignWorkoutRequest(@NotNull Long workoutId) {}
