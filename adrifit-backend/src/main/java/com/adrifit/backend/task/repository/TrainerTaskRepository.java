package com.adrifit.backend.task.repository;

import com.adrifit.backend.task.domain.TaskStatus;
import com.adrifit.backend.task.domain.TaskType;
import com.adrifit.backend.task.domain.TrainerTask;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainerTaskRepository extends JpaRepository<TrainerTask, Long> {

    List<TrainerTask> findAllByOrderByStatusAscDueDateAscIdAsc();

    List<TrainerTask> findByStatusOrderByDueDateAscIdAsc(TaskStatus status);

    List<TrainerTask> findByClientIdOrderByStatusAscDueDateAscIdAsc(Long clientId);

    List<TrainerTask> findByClientIdAndTypeAndStatus(Long clientId, TaskType type, TaskStatus status);

    boolean existsByClientIdAndTypeAndStatus(Long clientId, TaskType type, TaskStatus status);

    long countByStatusAndDueDateLessThanEqual(TaskStatus status, LocalDate date);

    List<TrainerTask> findByClientId(Long clientId);
}
