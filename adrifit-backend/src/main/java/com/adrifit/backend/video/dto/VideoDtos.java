package com.adrifit.backend.video.dto;

import com.adrifit.backend.video.domain.VideoSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class VideoDtos {

    private VideoDtos() {
    }

    public record VideoResponse(
            Long id,
            Long clientId,
            String clientName,
            VideoSource source,
            String exerciseName,
            String note,
            String contentType,
            long fileSize,
            Integer durationSeconds,
            String feedback,
            Instant reviewedAt,
            /** For the client: the trainer's video or correction is new. */
            boolean unseen,
            Instant createdAt
    ) {
    }

    public record FeedbackRequest(
            @NotBlank(message = "Escribe la corrección")
            @Size(max = 2000, message = "La corrección no puede superar los 2000 caracteres")
            String feedback
    ) {
    }

    /** Short-lived signed URL: a {@code <video>} element can stream it (range requests) without headers. */
    public record StreamLink(String url, String downloadUrl, Instant expiresAt) {
    }

    public record VideoCounts(long pendingReview) {
    }
}
