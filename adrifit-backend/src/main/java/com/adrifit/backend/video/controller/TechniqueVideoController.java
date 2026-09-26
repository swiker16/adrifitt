package com.adrifit.backend.video.controller;

import com.adrifit.backend.video.domain.TechniqueVideo;
import com.adrifit.backend.video.dto.VideoDtos.FeedbackRequest;
import com.adrifit.backend.video.dto.VideoDtos.StreamLink;
import com.adrifit.backend.video.dto.VideoDtos.VideoCounts;
import com.adrifit.backend.video.dto.VideoDtos.VideoResponse;
import com.adrifit.backend.video.service.TechniqueVideoService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
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
public class TechniqueVideoController {

    private static final DateTimeFormatter FILE_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Madrid"));

    private final TechniqueVideoService service;

    public TechniqueVideoController(TechniqueVideoService service) {
        this.service = service;
    }

    // ── Client ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/api/videos/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<VideoResponse> upload(@RequestParam("file") MultipartFile file,
                                                @RequestParam("exerciseName") String exerciseName,
                                                @RequestParam(value = "note", required = false) String note,
                                                @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadMine(file, exerciseName, note, durationSeconds));
    }

    @GetMapping("/api/videos/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<VideoResponse>> findMine() {
        return ResponseEntity.ok(service.findMine());
    }

    @PostMapping("/api/videos/me/seen")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> markSeen() {
        service.markSeenByCurrentClient();
        return ResponseEntity.noContent().build();
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    @GetMapping("/api/videos")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<VideoResponse>> findAll(@RequestParam(value = "pending", defaultValue = "false") boolean pending) {
        return ResponseEntity.ok(service.findAll(pending));
    }

    @GetMapping("/api/videos/counts")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<VideoCounts> counts() {
        return ResponseEntity.ok(new VideoCounts(service.countPendingReview()));
    }

    @GetMapping("/api/clients/{clientId}/videos")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<VideoResponse>> findForClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(service.findForClient(clientId));
    }

    @PostMapping(value = "/api/clients/{clientId}/videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<VideoResponse> uploadForClient(@PathVariable Long clientId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam("exerciseName") String exerciseName,
                                                         @RequestParam(value = "note", required = false) String note,
                                                         @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.uploadForClient(clientId, file, exerciseName, note, durationSeconds));
    }

    @PatchMapping("/api/videos/{id}/feedback")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<VideoResponse> review(@PathVariable Long id, @Valid @RequestBody FeedbackRequest request) {
        return ResponseEntity.ok(service.review(id, request.feedback()));
    }

    // ── Shared ──────────────────────────────────────────────────────────────

    @DeleteMapping("/api/videos/{id}")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/videos/{id}/link")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<StreamLink> link(@PathVariable Long id) {
        return ResponseEntity.ok(service.streamLink(id));
    }

    /**
     * Public but signed (see {@code VideoLinkSigner}). Returning a {@link Resource} lets Spring
     * answer HTTP range requests (206), which browsers use to stream and seek videos.
     */
    @GetMapping("/api/videos/{id}/stream")
    public ResponseEntity<Resource> stream(@PathVariable Long id,
                                           @RequestParam("exp") long exp,
                                           @RequestParam("sig") String sig,
                                           @RequestParam(value = "download", defaultValue = "false") boolean download) {
        TechniqueVideo video = service.getBySignedLink(id, exp, sig);
        // iPhone .mov files use an MP4-compatible container: Chrome only plays them announced as video/mp4.
        String type = !download && "video/quicktime".equals(video.getContentType()) ? "video/mp4" : video.getContentType();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(type))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        if (download) {
            String key = video.getStorageKey();
            String name = video.getExerciseName().replaceAll("[^\\p{L}\\p{N} _-]", "").trim().replace(' ', '-')
                    + "-" + FILE_DATE.format(video.getCreatedAt()) + key.substring(key.lastIndexOf('.'));
            response.header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString());
        }
        return response.body(service.openContent(video));
    }
}
