package com.adrifit.backend.report.dto;

import com.adrifit.backend.report.domain.ReportStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record WeeklyReportResponse(
        Long id,
        Long clientId,
        String clientFirstName,
        String clientLastName,
        BigDecimal weight,
        BigDecimal waist,
        BigDecimal bodyFat,
        Integer energyLevel,
        Integer dietAdherence,
        Integer trainingAdherence,
        String comments,
        String coachFeedback,
        ReportStatus status,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt,
        java.util.List<com.adrifit.backend.photo.dto.PhotoDtos.PhotoResponse> photos
) {
}
