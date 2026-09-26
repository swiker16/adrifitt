package com.adrifit.backend.workoutlog.controller;

import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.ExerciseProgressResponse;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.LogTemplateResponse;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.SaveWorkoutLogRequest;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.WorkoutDayResponse;
import com.adrifit.backend.workoutlog.dto.WorkoutLogDtos.WorkoutLogResponse;
import com.adrifit.backend.workoutlog.service.WorkoutLogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkoutLogController {

    private final WorkoutLogService service;

    public WorkoutLogController(WorkoutLogService service) {
        this.service = service;
    }

    // ── Client ──────────────────────────────────────────────────────────────

    @GetMapping("/api/workout-logs/me/days")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<WorkoutDayResponse>> myDays() {
        return ResponseEntity.ok(service.getMyDays());
    }

    @GetMapping("/api/workout-logs/me/template")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<LogTemplateResponse> myTemplate(@RequestParam Integer dayNumber) {
        return ResponseEntity.ok(service.getMyTemplate(dayNumber));
    }

    @GetMapping("/api/workout-logs/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<WorkoutLogResponse>> findMine() {
        return ResponseEntity.ok(service.findMine());
    }

    @GetMapping("/api/workout-logs/me/progress")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<ExerciseProgressResponse>> myProgress() {
        return ResponseEntity.ok(service.myProgress());
    }

    @GetMapping("/api/workout-logs/me/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<WorkoutLogResponse> findMineById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findMineById(id));
    }

    @PostMapping("/api/workout-logs/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<WorkoutLogResponse> create(@Valid @RequestBody SaveWorkoutLogRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createMine(request));
    }

    @PutMapping("/api/workout-logs/me/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<WorkoutLogResponse> update(@PathVariable Long id, @Valid @RequestBody SaveWorkoutLogRequest request) {
        return ResponseEntity.ok(service.updateMine(id, request));
    }

    @DeleteMapping("/api/workout-logs/me/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteMine(id);
        return ResponseEntity.noContent().build();
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    @GetMapping("/api/clients/{clientId}/workout-logs")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<WorkoutLogResponse>> findForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(service.findForClient(clientId));
    }

    @GetMapping("/api/clients/{clientId}/workout-logs/progress")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<ExerciseProgressResponse>> progressForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(service.progressForClient(clientId));
    }
}
