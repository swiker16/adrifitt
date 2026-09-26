package com.adrifit.backend.report.mapper;

import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReportMapper {

    public WeeklyReportResponse toResponse(WeeklyReport report) {
        return toResponse(report, java.util.List.of());
    }

    public WeeklyReportResponse toResponse(WeeklyReport report,
                                           java.util.List<com.adrifit.backend.photo.dto.PhotoDtos.PhotoResponse> photos) {
        return new WeeklyReportResponse(
                report.getId(),
                report.getClient().getId(),
                report.getClient().getFirstName(),
                report.getClient().getLastName(),
                report.getWeight(),
                report.getWaist(),
                report.getBodyFat(),
                report.getEnergyLevel(),
                report.getDietAdherence(),
                report.getTrainingAdherence(),
                report.getComments(),
                report.getCoachFeedback(),
                report.getStatus(),
                report.getReviewedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt(),
                photos
        );
    }
}
