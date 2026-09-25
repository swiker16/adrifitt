package com.adrifit.backend.photo.dto;

import com.adrifit.backend.photo.domain.PhotoPose;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class PhotoDtos {

    private PhotoDtos() {
    }

    public record PhotoResponse(
            Long id,
            Long clientId,
            LocalDate takenOn,
            PhotoPose pose,
            String notes,
            String trainerComment,
            String contentType,
            Long fileSize,
            Instant uploadedAt,
            /* Relative API path; must be fetched with the Authorization header. */
            String contentUrl
    ) {
    }

    public record PhotoCommentRequest(@Size(max = 1000) String comment) {
    }
}
