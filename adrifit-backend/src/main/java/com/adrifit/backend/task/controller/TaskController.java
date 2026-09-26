package com.adrifit.backend.task.controller;

import com.adrifit.backend.task.domain.TaskStatus;
import com.adrifit.backend.task.dto.TaskDtos.ReviewScheduleItem;
import com.adrifit.backend.task.dto.TaskDtos.SaveTaskRequest;
import com.adrifit.backend.task.dto.TaskDtos.TaskResponse;
import com.adrifit.backend.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('TRAINER')")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/api/tasks")
    public ResponseEntity<List<TaskResponse>> findAll(@RequestParam(required = false) TaskStatus status,
                                                      @RequestParam(required = false) Long clientId) {
        return ResponseEntity.ok(taskService.findAll(status, clientId));
    }

    @PostMapping("/api/tasks")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody SaveTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(request));
    }

    @PutMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody SaveTaskRequest request) {
        return ResponseEntity.ok(taskService.update(id, request));
    }

    @PatchMapping("/api/tasks/{id}/complete")
    public ResponseEntity<TaskResponse> complete(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.complete(id));
    }

    @PatchMapping("/api/tasks/{id}/reopen")
    public ResponseEntity<TaskResponse> reopen(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.reopen(id));
    }

    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/reviews/schedule")
    public ResponseEntity<List<ReviewScheduleItem>> reviewSchedule() {
        return ResponseEntity.ok(taskService.reviewSchedule());
    }
}
