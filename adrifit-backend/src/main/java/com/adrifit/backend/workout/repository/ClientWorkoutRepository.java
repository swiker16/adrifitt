package com.adrifit.backend.workout.repository;

import com.adrifit.backend.workout.domain.ClientWorkout;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientWorkoutRepository extends JpaRepository<ClientWorkout, Long> {

    Optional<ClientWorkout> findByClientIdAndActiveTrue(Long clientId);

    List<ClientWorkout> findAllByClientIdOrderByAssignedAtDesc(Long clientId);

    List<ClientWorkout> findAllByWorkoutId(Long workoutId);

    List<ClientWorkout> findTop10ByOrderByAssignedAtDesc();
}
