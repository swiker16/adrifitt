package com.adrifit.backend.dashboard.controller;

import com.adrifit.backend.dashboard.dto.ClientDashboardResponse;
import com.adrifit.backend.dashboard.dto.TrainerDashboardResponse;
import com.adrifit.backend.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/trainer")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<TrainerDashboardResponse> trainerDashboard() {
        return ResponseEntity.ok(dashboardService.getTrainerDashboard());
    }

    @GetMapping("/client")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ClientDashboardResponse> clientDashboard() {
        return ResponseEntity.ok(dashboardService.getClientDashboard());
    }
}
