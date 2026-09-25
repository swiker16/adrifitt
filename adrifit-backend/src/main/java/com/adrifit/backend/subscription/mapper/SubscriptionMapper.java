package com.adrifit.backend.subscription.mapper;

import com.adrifit.backend.plan.mapper.PlanMapper;
import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionMapper {

    private final PlanMapper planMapper;

    public SubscriptionMapper(PlanMapper planMapper) {
        this.planMapper = planMapper;
    }

    public SubscriptionResponse toResponse(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getClientId(),
                subscription.getPlan().getId(),
                subscription.getPlan().getName(),
                subscription.getStartDate(),
                subscription.getEndDate(),
                subscription.getRenewalDate(),
                subscription.getStatus(),
                subscription.isActive(),
                subscription.isCancelAtPeriodEnd(),
                subscription.getCancelledAt(),
                planMapper.toResponse(subscription.getPlan())
        );
    }
}
