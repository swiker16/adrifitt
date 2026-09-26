package com.adrifit.backend.analysis.dto;

import com.adrifit.backend.analysis.domain.AnalysisStatus;
import java.time.Instant;
import java.time.LocalDate;

public record AnalysisResponse(
        Long id,
        Long clientId,
        String clientName,
        String title,
        LocalDate analysisDate,
        String clientComment,
        String trainerInternalNote,
        Instant uploadedAt,
        Instant reviewedAt,
        AnalysisStatus status,
        String originalFileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        String viewUrl
) {
}
