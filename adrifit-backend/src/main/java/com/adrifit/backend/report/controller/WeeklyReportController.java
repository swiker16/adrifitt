package com.adrifit.backend.report.controller;

import com.adrifit.backend.report.dto.CoachFeedbackRequest;
import com.adrifit.backend.report.dto.CreateWeeklyReportRequest;
import com.adrifit.backend.report.dto.UpdateWeeklyReportRequest;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import com.adrifit.backend.report.service.WeeklyReportService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WeeklyReportController {

    private final WeeklyReportService reportService;

    public WeeklyReportController(WeeklyReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/reports")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<WeeklyReportResponse> createMine(@Valid @RequestBody CreateWeeklyReportRequest request) {
        WeeklyReportResponse created = reportService.createForCurrentClient(request);
        return ResponseEntity.created(URI.create("/api/reports/" + created.id())).body(created);
    }

    @GetMapping("/api/reports/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<WeeklyReportResponse>> findMine() {
        return ResponseEntity.ok(reportService.findByCurrentClient());
    }

    @GetMapping("/api/reports")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<WeeklyReportResponse>> findAll() {
        return ResponseEntity.ok(reportService.findAll());
    }

    @GetMapping("/api/reports/pending")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<WeeklyReportResponse>> findPending() {
        return ResponseEntity.ok(reportService.findPending());
    }

    @PostMapping("/api/clients/{clientId}/reports")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<WeeklyReportResponse> create(@PathVariable Long clientId,
                                                       @Valid @RequestBody CreateWeeklyReportRequest request) {
        WeeklyReportResponse created = reportService.create(clientId, request);
        return ResponseEntity.created(URI.create("/api/reports/" + created.id())).body(created);
    }

    @GetMapping("/api/clients/{clientId}/reports")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<List<WeeklyReportResponse>> findByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(reportService.findByClientId(clientId));
    }

    @GetMapping("/api/reports/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<WeeklyReportResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.findById(id));
    }

    @PutMapping("/api/reports/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<WeeklyReportResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateWeeklyReportRequest request) {
        return ResponseEntity.ok(reportService.update(id, request));
    }

    @DeleteMapping("/api/reports/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reportService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/reports/{id}/feedback")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<WeeklyReportResponse> setFeedback(@PathVariable Long id,
                                                            @Valid @RequestBody CoachFeedbackRequest request) {
        return ResponseEntity.ok(reportService.setCoachFeedback(id, request));
    }
}
