package com.adrifit.backend.workoutlog.repository;

import com.adrifit.backend.workoutlog.domain.WorkoutLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutLogRepository extends JpaRepository<WorkoutLog, Long> {

    List<WorkoutLog> findByClientIdOrderByPerformedOnDescIdDesc(Long clientId);

    List<WorkoutLog> findByClientIdOrderByPerformedOnAscIdAsc(Long clientId);

    Optional<WorkoutLog> findByClientIdAndPerformedOnAndDayNumber(Long clientId, LocalDate performedOn, Integer dayNumber);

    Optional<WorkoutLog> findFirstByClientIdAndDayNumberOrderByPerformedOnDescIdDesc(Long clientId, Integer dayNumber);

    long countByClientIdAndPerformedOnBetween(Long clientId, LocalDate from, LocalDate to);

    long countByClientId(Long clientId);

    List<WorkoutLog> findTop10ByOrderByCreatedAtDesc();
}
