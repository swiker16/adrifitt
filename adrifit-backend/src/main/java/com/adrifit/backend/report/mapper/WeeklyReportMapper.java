package com.adrifit.backend.report.mapper;

import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import org.springframework.stereotype.Component;

@Component
public class WeeklyReportMapper {

    public WeeklyReportResponse toResponse(WeeklyReport report) {
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
                report.getUpdatedAt()
        );
    }
}
