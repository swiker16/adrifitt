package com.adrifit.backend.analysis.controller;

import com.adrifit.backend.analysis.dto.AnalysisResponse;
import com.adrifit.backend.analysis.dto.ReviewAnalysisRequest;
import com.adrifit.backend.analysis.dto.UploadAnalysisRequest;
import com.adrifit.backend.analysis.service.AnalysisService;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AnalysisController {

    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    // ── CLIENT endpoints ──────────────────────────────────────────────────

    @PostMapping(value = "/api/client/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AnalysisResponse> upload(
            @RequestParam("title") String title,
            @RequestParam("analysisDate") String analysisDate,
            @RequestParam(value = "clientComment", required = false) String clientComment,
            @RequestParam("file") MultipartFile file) {
        UploadAnalysisRequest request = new UploadAnalysisRequest(
                title,
                java.time.LocalDate.parse(analysisDate),
                clientComment
        );
        AnalysisResponse response = service.uploadForCurrentClient(request, file);
        return ResponseEntity.created(URI.create("/api/client/analyses/" + response.id())).body(response);
    }

    @GetMapping("/api/client/analyses")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<AnalysisResponse>> listMine() {
        return ResponseEntity.ok(service.getMyAnalyses());
    }

    @GetMapping("/api/client/analyses/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AnalysisResponse> getMine(@PathVariable Long id) {
        return ResponseEntity.ok(service.getMyAnalysis(id));
    }

    @GetMapping("/api/client/analyses/{id}/content")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<InputStreamResource> getContentClient(
            @PathVariable Long id,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition) {
        InputStream stream = service.getContentForClient(id);
        return buildPdfResponse(stream, disposition);
    }

    @DeleteMapping("/api/client/analyses/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> deleteMine(@PathVariable Long id) {
        service.deleteMyAnalysis(id);
        return ResponseEntity.noContent().build();
    }

    // ── TRAINER endpoints ─────────────────────────────────────────────────

    @GetMapping("/api/clients/{clientId}/analyses")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<AnalysisResponse>> listForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(service.getAnalysesForClient(clientId));
    }

    @GetMapping("/api/analyses/{id}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<AnalysisResponse> getForTrainer(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAnalysisForTrainer(id));
    }

    @GetMapping("/api/analyses/{id}/content")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<InputStreamResource> getContentTrainer(
            @PathVariable Long id,
            @RequestParam(value = "disposition", defaultValue = "inline") String disposition) {
        InputStream stream = service.getContentForTrainer(id);
        return buildPdfResponse(stream, disposition);
    }

    @PatchMapping("/api/analyses/{id}/review")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<AnalysisResponse> review(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewAnalysisRequest request) {
        ReviewAnalysisRequest req = request != null ? request : new ReviewAnalysisRequest(null);
        return ResponseEntity.ok(service.review(id, req));
    }

    @GetMapping("/api/analyses/pending-count")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Long> pendingCount() {
        return ResponseEntity.ok(service.countPendingAnalyses());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ResponseEntity<InputStreamResource> buildPdfResponse(InputStream stream, String disposition) {
        String contentDisposition = "attachment".equals(disposition)
                ? "attachment; filename=\"analytic.pdf\""
                : "inline; filename=\"analytic.pdf\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(new InputStreamResource(stream));
    }
}
