package com.adrifit.backend.workoutlog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class WorkoutLogDtos {

    private WorkoutLogDtos() {
    }

    /** A selectable day of the client's active routine. */
    public record WorkoutDayResponse(Integer dayNumber, String dayName, int exerciseCount, int totalSets) {
    }

    /** Pre-filled form for logging a day: one row per set of every exercise of that day. */
    public record LogTemplateResponse(
            Long workoutId,
            String workoutName,
            Integer dayNumber,
            String dayName,
            List<TemplateExercise> exercises
    ) {
    }

    public record TemplateExercise(
            Long exerciseId,
            String exerciseName,
            int sets,
            Integer targetReps,
            String approxReps,
            Integer targetRir,
            Integer restSeconds,
            String notes,
            /* Values of the last time this day was logged (to help the client progress). */
            List<SetValue> previous
    ) {
    }

    public record SetValue(Integer setNumber, BigDecimal weightKg, Integer reps, Integer rir) {
    }

    public record SaveWorkoutLogRequest(
            @NotNull(message = "La fecha es obligatoria") LocalDate performedOn,
            @NotNull(message = "El día de la rutina es obligatorio") @Min(1) Integer dayNumber,
            @Size(max = 1000) String notes,
            @NotEmpty(message = "Registra al menos una serie") @Valid List<LogSetRequest> sets
    ) {
    }

    public record LogSetRequest(
            @NotNull(message = "Ejercicio obligatorio") Long exerciseId,
            @NotNull @Min(1) Integer setNumber,
            @NotNull(message = "Indica el peso de la serie")
            @DecimalMin(value = "0.0", message = "El peso no puede ser negativo")
            @DecimalMax(value = "1000.0", message = "Peso no válido") BigDecimal weightKg,
            @Min(0) @Max(200) Integer reps,
            @NotNull(message = "Indica el RIR de la serie") @Min(0) @Max(10) Integer rir
    ) {
    }

    public record WorkoutLogResponse(
            Long id,
            Long clientId,
            Long workoutId,
            String workoutName,
            Integer dayNumber,
            String dayName,
            LocalDate performedOn,
            String notes,
            int totalSets,
            BigDecimal totalVolumeKg,
            List<LoggedExercise> exercises,
            Instant createdAt
    ) {
    }

    public record LoggedExercise(Long exerciseId, String exerciseName, List<SetValue> sets) {
    }

    public record ExerciseProgressResponse(String exerciseName, List<ExerciseProgressPoint> points) {
    }

    public record ExerciseProgressPoint(
            LocalDate date,
            BigDecimal maxWeightKg,
            BigDecimal volumeKg,
            BigDecimal estimatedOneRepMaxKg,
            Integer bestRir
    ) {
    }
}
