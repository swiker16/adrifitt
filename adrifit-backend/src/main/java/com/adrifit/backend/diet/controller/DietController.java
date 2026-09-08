package com.adrifit.backend.diet.controller;

import com.adrifit.backend.diet.dto.AssignDietRequest;
import com.adrifit.backend.diet.dto.ClientDietResponse;
import com.adrifit.backend.diet.dto.CreateDietRequest;
import com.adrifit.backend.diet.dto.DietResponse;
import com.adrifit.backend.diet.dto.DietSummaryResponse;
import com.adrifit.backend.diet.service.DietPdfService;
import com.adrifit.backend.diet.service.DietService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
public class DietController {

    private final DietService dietService;
    private final DietPdfService dietPdfService;

    public DietController(DietService dietService, DietPdfService dietPdfService) {
        this.dietService = dietService;
        this.dietPdfService = dietPdfService;
    }

    // ── Trainer: CRUD diets ────────────────────────────────────────────────

    @GetMapping("/api/trainer/diets")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<DietSummaryResponse>> findAll() {
        return ResponseEntity.ok(dietService.findAll());
    }

    @GetMapping("/api/trainer/diets/{dietId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<DietResponse> findById(@PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.findById(dietId));
    }

    @PostMapping("/api/trainer/diets")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<DietResponse> create(@Valid @RequestBody CreateDietRequest request) {
        DietResponse created = dietService.create(request);
        return ResponseEntity.created(URI.create("/api/trainer/diets/" + created.id())).body(created);
    }

    @PutMapping("/api/trainer/diets/{dietId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<DietResponse> update(@PathVariable Long dietId,
                                               @Valid @RequestBody CreateDietRequest request) {
        return ResponseEntity.ok(dietService.update(dietId, request));
    }

    @DeleteMapping("/api/trainer/diets/{dietId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Void> delete(@PathVariable Long dietId) {
        dietService.delete(dietId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/trainer/diets/{dietId}/duplicate")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<DietResponse> duplicate(@PathVariable Long dietId) {
        DietResponse copy = dietService.duplicate(dietId);
        return ResponseEntity.created(URI.create("/api/trainer/diets/" + copy.id())).body(copy);
    }

    @PatchMapping("/api/trainer/diets/{dietId}/status")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<DietResponse> toggleStatus(@PathVariable Long dietId) {
        return ResponseEntity.ok(dietService.toggleStatus(dietId));
    }

    // ── Trainer: client-diet management ───────────────────────────────────

    @PostMapping("/api/clients/{clientId}/diets/assign")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<ClientDietResponse> assignToClient(
            @PathVariable Long clientId,
            @Valid @RequestBody AssignDietRequest request) {
        return ResponseEntity.ok(dietService.assignToClient(clientId, request));
    }

    @GetMapping("/api/clients/{clientId}/diets")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<ClientDietResponse>> getHistoryForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(dietService.getHistoryForClient(clientId));
    }

    @GetMapping("/api/clients/{clientId}/diets/active")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<ClientDietResponse> getActiveForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(dietService.getActiveForClient(clientId));
    }

    @GetMapping("/api/clients/{clientId}/diets/active/pdf")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<byte[]> exportPdfForClient(@PathVariable Long clientId) {
        byte[] pdf = dietPdfService.generateForClient(clientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dieta-cliente-" + clientId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── Client: own diet ──────────────────────────────────────────────────

    @GetMapping("/api/client/diet")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ClientDietResponse> getMyDiet() {
        return ResponseEntity.ok(dietService.getMyDiet());
    }

    @GetMapping("/api/client/diet/pdf")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<byte[]> getMyDietPdf() {
        Long clientId = dietService.getMyDiet().clientId();
        byte[] pdf = dietPdfService.generateForClient(clientId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mi-dieta.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
