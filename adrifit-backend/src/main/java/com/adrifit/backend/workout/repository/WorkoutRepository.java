package com.adrifit.backend.workout.repository;

import com.adrifit.backend.workout.domain.Workout;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    List<Workout> findAllByOrderByCreatedAtDesc();

    boolean existsByNameIgnoreCase(String name);
}
