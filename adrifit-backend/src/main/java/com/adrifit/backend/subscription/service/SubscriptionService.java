package com.adrifit.backend.subscription.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecurityUtils;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.service.PlanService;
import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import com.adrifit.backend.subscription.mapper.SubscriptionMapper;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.service.UserService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ClientService clientService;
    private final PlanService planService;
    private final UserService userService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionMapper subscriptionMapper,
                               ClientService clientService,
                               PlanService planService,
                               UserService userService) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionMapper = subscriptionMapper;
        this.clientService = clientService;
        this.planService = planService;
        this.userService = userService;
    }

    /**
     * Assigns a new plan to a client. Closes the current active subscription (sets end_date +
     * active=false) and creates a fresh one. This builds a full subscription history.
     */
    @Transactional
    public SubscriptionResponse assignPlan(Long clientId, AssignPlanRequest request) {
        Client client = clientService.getEntityById(clientId);
        Plan plan = planService.getEntityById(request.planId());
        if (!plan.isActive()) {
            throw new BusinessException("Cannot assign an inactive plan");
        }

        LocalDate today = LocalDate.now();

        // Close any existing active subscription
        subscriptionRepository.findByClientIdAndActiveTrue(client.getId()).ifPresent(current -> {
            current.setEndDate(today);
            current.setActive(false);
            current.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(current);
        });

        // Create new subscription
        Subscription subscription = Subscription.builder()
                .clientId(client.getId())
                .plan(plan)
                .startDate(today)
                .renewalDate(computeRenewalDate(today, plan))
                .status(SubscriptionStatus.ACTIVE)
                .active(true)
                .build();

        return subscriptionMapper.toResponse(subscriptionRepository.save(subscription));
    }

    public SubscriptionResponse getActiveByClientId(Long clientId) {
        assertCanAccessClient(clientId);
        return subscriptionMapper.toResponse(getActiveOrThrow(clientId));
    }

    public List<SubscriptionResponse> getHistoryByClientId(Long clientId) {
        assertCanAccessClient(clientId);
        return subscriptionRepository.findByClientIdOrderByStartDateDesc(clientId)
                .stream().map(subscriptionMapper::toResponse).toList();
    }

    public SubscriptionResponse getMySubscription() {
        return subscriptionMapper.toResponse(getActiveOrThrow(getCurrentClientId()));
    }

    private LocalDate computeRenewalDate(LocalDate from, Plan plan) {
        return from.plusDays(plan.getReviewFrequencyDays());
    }

    private Subscription getActiveOrThrow(Long clientId) {
        return subscriptionRepository.findByClientIdAndActiveTrue(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for client: " + clientId));
    }

    private void assertCanAccessClient(Long clientId) {
        if (SecurityUtils.isTrainer()) {
            return;
        }
        if (!getCurrentClientId().equals(clientId)) {
            throw new AccessDeniedException("You can only access your own subscription");
        }
    }

    private Long getCurrentClientId() {
        User currentUser = userService.getCurrentUser();
        return clientService.getByUserId(currentUser.getId()).getId();
    }
}
