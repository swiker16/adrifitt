package com.adrifit.backend.plan.mapper;

import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.dto.PlanResponse;
import org.springframework.stereotype.Component;

@Component
public class PlanMapper {

    public PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getMonthlyPrice(),
                plan.getReviewFrequencyDays(),
                plan.isMessagingEnabled(),
                plan.isAnalyticsEnabled(),
                plan.isPdfExportEnabled(),
                plan.isPrioritySupport(),
                plan.isActive(),
                plan.getCreatedAt()
        );
    }
}
