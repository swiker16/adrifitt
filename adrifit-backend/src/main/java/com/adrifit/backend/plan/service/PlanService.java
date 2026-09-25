package com.adrifit.backend.plan.service;

import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.dto.CreatePlanRequest;
import com.adrifit.backend.plan.dto.PlanResponse;
import com.adrifit.backend.plan.dto.UpdatePlanRequest;
import com.adrifit.backend.plan.mapper.PlanMapper;
import com.adrifit.backend.plan.repository.PlanRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    public PlanService(PlanRepository planRepository, PlanMapper planMapper) {
        this.planRepository = planRepository;
        this.planMapper = planMapper;
    }

    @Transactional
    public PlanResponse create(CreatePlanRequest request) {
        if (planRepository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("A plan with this name already exists");
        }
        Plan plan = planRepository.save(Plan.builder()
                .name(request.name())
                .description(request.description())
                .monthlyPrice(request.monthlyPrice())
                .reviewFrequencyDays(request.reviewFrequencyDays())
                .messagingEnabled(request.messagingEnabled())
                .analyticsEnabled(request.analyticsEnabled())
                .pdfExportEnabled(request.pdfExportEnabled())
                .prioritySupport(request.prioritySupport())
                .active(true)
                .build());
        return planMapper.toResponse(plan);
    }

    public List<PlanResponse> findAll(boolean activeOnly) {
        List<Plan> plans = activeOnly
                ? planRepository.findByActiveTrueOrderByMonthlyPriceAsc()
                : planRepository.findAllByOrderByMonthlyPriceAsc();
        return plans.stream().map(planMapper::toResponse).toList();
    }

    public PlanResponse findById(Long id) {
        return planMapper.toResponse(getPlanOrThrow(id));
    }

    @Transactional
    public PlanResponse update(Long id, UpdatePlanRequest request) {
        Plan plan = getPlanOrThrow(id);
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setMonthlyPrice(request.monthlyPrice());
        plan.setReviewFrequencyDays(request.reviewFrequencyDays());
        plan.setMessagingEnabled(request.messagingEnabled());
        plan.setAnalyticsEnabled(request.analyticsEnabled());
        plan.setPdfExportEnabled(request.pdfExportEnabled());
        plan.setPrioritySupport(request.prioritySupport());
        plan.setActive(request.active());
        return planMapper.toResponse(planRepository.save(plan));
    }

    @Transactional
    public PlanResponse setActive(Long id, boolean active) {
        Plan plan = getPlanOrThrow(id);
        plan.setActive(active);
        return planMapper.toResponse(planRepository.save(plan));
    }

    @Transactional
    public void deletePermanently(Long id) {
        Plan plan = getPlanOrThrow(id);
        if (planRepository.countActiveSubscriptionsByPlanId(id) > 0) {
            throw new BusinessException("No se puede eliminar un plan con suscripciones activas");
        }
        if (planRepository.countSubscriptionsByPlanId(id) > 0) {
            // Past subscriptions (and their payments) reference the plan: keep it for the history.
            throw new BusinessException("Este plan tiene historial de suscripciones. Desactívalo en lugar de eliminarlo.");
        }
        planRepository.delete(plan);
    }

    public Plan getEntityById(Long id) {
        return getPlanOrThrow(id);
    }

    private Plan getPlanOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
    }
}
