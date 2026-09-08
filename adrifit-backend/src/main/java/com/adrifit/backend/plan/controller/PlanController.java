package com.adrifit.backend.plan.controller;

import com.adrifit.backend.plan.dto.CreatePlanRequest;
import com.adrifit.backend.plan.dto.PlanResponse;
import com.adrifit.backend.plan.dto.UpdatePlanRequest;
import com.adrifit.backend.plan.service.PlanService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        PlanResponse created = planService.create(request);
        return ResponseEntity.created(URI.create("/api/plans/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<PlanResponse>> findAll(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        return ResponseEntity.ok(planService.findAll(activeOnly));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(planService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PlanResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.ok(planService.update(id, request));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PlanResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(planService.setActive(id, false));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Void> deletePermanently(@PathVariable Long id) {
        planService.deletePermanently(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PlanResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(planService.setActive(id, true));
    }
}
