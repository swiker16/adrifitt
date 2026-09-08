package com.adrifit.backend.workout.mapper;

import com.adrifit.backend.workout.domain.ClientWorkout;
import com.adrifit.backend.workout.domain.Workout;
import com.adrifit.backend.workout.domain.WorkoutExercise;
import com.adrifit.backend.workout.dto.ClientWorkoutResponse;
import com.adrifit.backend.workout.dto.WorkoutExerciseResponse;
import com.adrifit.backend.workout.dto.WorkoutResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkoutMapper {

    public WorkoutExerciseResponse toExerciseResponse(WorkoutExercise e) {
        return new WorkoutExerciseResponse(
                e.getId(),
                e.getExerciseName(),
                e.getSets(),
                e.getReps(),
                e.getRir(),
                e.getRestSeconds(),
                e.getNotes(),
                e.getOrderIndex(),
                e.getDayNumber(),
                e.getDayName(),
                e.getWarmUpSets(),
                e.getApproxReps()
        );
    }

    public WorkoutResponse toResponse(Workout w) {
        List<WorkoutExerciseResponse> exercises = w.getExercises() == null
                ? List.of()
                : w.getExercises().stream().map(this::toExerciseResponse).toList();
        return new WorkoutResponse(
                w.getId(),
                w.getName(),
                w.getDescription(),
                w.getObjective(),
                w.getDaysPerWeek(),
                w.getCreatedAt(),
                exercises
        );
    }

    public ClientWorkoutResponse toClientWorkoutResponse(ClientWorkout cw) {
        return new ClientWorkoutResponse(
                cw.getId(),
                cw.getClientId(),
                toResponse(cw.getWorkout()),
                cw.getAssignedAt(),
                cw.getEndDate(),
                cw.isActive()
        );
    }
}
