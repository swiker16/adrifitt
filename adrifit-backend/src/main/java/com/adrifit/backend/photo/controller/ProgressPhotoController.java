package com.adrifit.backend.photo.controller;

import com.adrifit.backend.photo.domain.PhotoPose;
import com.adrifit.backend.photo.domain.ProgressPhoto;
import com.adrifit.backend.photo.dto.PhotoDtos.PhotoCommentRequest;
import com.adrifit.backend.photo.dto.PhotoDtos.PhotoResponse;
import com.adrifit.backend.photo.service.ProgressPhotoService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
public class ProgressPhotoController {

    private final ProgressPhotoService service;

    public ProgressPhotoController(ProgressPhotoService service) {
        this.service = service;
    }

    // ── Client ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/api/photos/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<PhotoResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "takenOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenOn,
            @RequestParam(value = "pose", required = false) PhotoPose pose,
            @RequestParam(value = "notes", required = false) String notes) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadMine(file, takenOn, pose, notes));
    }

    @GetMapping("/api/photos/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<PhotoResponse>> findMine() {
        return ResponseEntity.ok(service.findMine());
    }

    @DeleteMapping("/api/photos/me/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> deleteMine(@PathVariable Long id) {
        service.deleteMine(id);
        return ResponseEntity.noContent().build();
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    @GetMapping("/api/photos/{id}/content")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<InputStreamResource> content(@PathVariable Long id) {
        ProgressPhoto photo = service.getAccessible(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(new InputStreamResource(service.openContent(photo)));
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    @GetMapping("/api/clients/{clientId}/photos")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<PhotoResponse>> findForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(service.findForClient(clientId));
    }

    @PatchMapping("/api/photos/{id}/comment")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<PhotoResponse> comment(@PathVariable Long id, @Valid @RequestBody PhotoCommentRequest request) {
        return ResponseEntity.ok(service.comment(id, request.comment()));
    }
}
