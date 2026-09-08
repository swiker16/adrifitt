package com.adrifit.backend.workout.controller;

import com.adrifit.backend.workout.dto.AssignWorkoutRequest;
import com.adrifit.backend.workout.dto.ClientWorkoutResponse;
import com.adrifit.backend.workout.dto.CreateWorkoutRequest;
import com.adrifit.backend.workout.dto.WorkoutResponse;
import com.adrifit.backend.workout.service.WorkoutPdfService;
import com.adrifit.backend.workout.service.WorkoutService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutService workoutService;
    private final WorkoutPdfService workoutPdfService;

    public WorkoutController(WorkoutService workoutService, WorkoutPdfService workoutPdfService) {
        this.workoutService = workoutService;
        this.workoutPdfService = workoutPdfService;
    }

    @GetMapping
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<WorkoutResponse>> findAll() {
        return ResponseEntity.ok(workoutService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<WorkoutResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(workoutService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<WorkoutResponse> create(@Valid @RequestBody CreateWorkoutRequest request) {
        WorkoutResponse created = workoutService.create(request);
        return ResponseEntity.created(URI.create("/api/workouts/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<WorkoutResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody CreateWorkoutRequest request) {
        return ResponseEntity.ok(workoutService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workoutService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<WorkoutResponse> duplicate(@PathVariable Long id) {
        WorkoutResponse copy = workoutService.duplicate(id);
        return ResponseEntity.created(URI.create("/api/workouts/" + copy.id())).body(copy);
    }

    @PostMapping("/clients/{clientId}/assign")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<ClientWorkoutResponse> assignToClient(
            @PathVariable Long clientId,
            @Valid @RequestBody AssignWorkoutRequest request) {
        return ResponseEntity.ok(workoutService.assignToClient(clientId, request));
    }

    @GetMapping("/clients/{clientId}/active")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<ClientWorkoutResponse> getActiveForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(workoutService.getActiveForClient(clientId));
    }

    @GetMapping("/clients/{clientId}/history")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<ClientWorkoutResponse>> getHistoryForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(workoutService.getHistoryForClient(clientId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ClientWorkoutResponse> getMyWorkout() {
        return ResponseEntity.ok(workoutService.getMyWorkout());
    }

    @GetMapping("/{workoutId}/pdf/{clientId}")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long workoutId,
                                            @PathVariable Long clientId) {
        byte[] pdf = workoutPdfService.generateWorkoutPdf(workoutId, clientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"rutina-" + workoutId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
