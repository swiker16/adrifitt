package com.adrifit.backend.subscription.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.FeatureNotAvailableException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.service.EmailService;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.plan.domain.BillingPeriod;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.service.PlanService;
import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.dto.UpdatePricingRequest;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import com.adrifit.backend.subscription.event.SubscriptionChargeEvent;
import com.adrifit.backend.subscription.event.SubscriptionClosedEvent;
import com.adrifit.backend.subscription.mapper.SubscriptionMapper;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscriptions are monthly: a new period starts every month on {@code renewalDate}, and each
 * period generates a charge (see payment module). Only one subscription per client is "current"
 * ({@code active=true}); the rest are history.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Safety limit when catching up renewals after a long downtime. */
    private static final int MAX_RENEWALS_PER_RUN = 12;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ClientService clientService;
    private final PlanService planService;
    private final ApplicationEventPublisher events;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               SubscriptionMapper subscriptionMapper,
                               ClientService clientService,
                               PlanService planService,
                               ApplicationEventPublisher events,
                               EmailService emailService,
                               EmailTemplates emailTemplates) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionMapper = subscriptionMapper;
        this.clientService = clientService;
        this.planService = planService;
        this.events = events;
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    /**
     * Assigns a new plan to a client. Closes the current subscription and creates a fresh one
     * (starting today) that generates its first charge.
     */
    @Transactional
    public SubscriptionResponse assignPlan(Long clientId, AssignPlanRequest request) {
        Client client = clientService.getEntityById(clientId);
        Plan plan = planService.getEntityById(request.planId());
        if (!plan.isActive()) {
            throw new BusinessException("No se puede asignar un plan inactivo");
        }
        BillingPeriod period = request.billingPeriod() != null ? request.billingPeriod() : BillingPeriod.MONTHLY;
        return subscriptionMapper.toResponse(startNewSubscription(client, plan, period,
                request.customPrice(), blankToNull(request.customPriceNote())));
    }

    /**
     * Special conditions for the client's current subscription. Also updates the charges of that
     * subscription that are still unpaid; paid ones keep their amount.
     */
    @Transactional
    public SubscriptionResponse updatePricing(Long clientId, UpdatePricingRequest request) {
        clientService.getEntityById(clientId);
        Subscription current = getActiveOrThrow(clientId);
        current.setCustomPrice(request.customPrice());
        current.setCustomPriceNote(request.customPrice() != null ? blankToNull(request.customPriceNote()) : null);
        Subscription saved = subscriptionRepository.save(current);
        events.publishEvent(new com.adrifit.backend.subscription.event.SubscriptionPriceChangedEvent(
                saved.getId(), saved.effectivePrice()));
        return subscriptionMapper.toResponse(saved);
    }

    @Transactional
    public SubscriptionResponse updateStatus(Long clientId, SubscriptionStatus status) {
        clientService.getEntityById(clientId);
        Subscription current = getActiveOrThrow(clientId);
        LocalDate today = LocalDate.now();
        switch (status) {
            case ACTIVE -> {
                current.setStatus(SubscriptionStatus.ACTIVE);
                current.setCancelAtPeriodEnd(false);
                current.setCancelledAt(null);
                if (current.getRenewalDate().isBefore(today)) {
                    current.setRenewalDate(today);
                }
            }
            case PAUSED -> current.setStatus(SubscriptionStatus.PAUSED);
            case CANCELLED -> close(current, today);
        }
        return subscriptionMapper.toResponse(subscriptionRepository.save(current));
    }

    // ── Client self-service ─────────────────────────────────────────────────

    public SubscriptionResponse getMySubscription() {
        return subscriptionMapper.toResponse(getActiveOrThrow(clientService.getCurrentClientId()));
    }

    public List<SubscriptionResponse> getMyHistory() {
        return subscriptionRepository.findByClientIdOrderByStartDateDesc(clientService.getCurrentClientId())
                .stream().map(subscriptionMapper::toResponse).toList();
    }

    /**
     * The client switches to another plan (or subscribes again after a cancellation). The new plan
     * starts today and is charged immediately; unpaid charges of the old subscription are voided.
     */
    @Transactional
    public SubscriptionResponse changeMyPlan(Long planId, BillingPeriod requestedPeriod) {
        Client client = clientService.getCurrentClient();
        Plan plan = planService.getEntityById(planId);
        if (!plan.isActive()) {
            throw new BusinessException("Ese plan ya no está disponible");
        }
        BillingPeriod period = requestedPeriod != null ? requestedPeriod : BillingPeriod.MONTHLY;
        Optional<Subscription> current = subscriptionRepository.findByClientIdAndActiveTrue(client.getId());
        if (current.isPresent()) {
            Subscription sub = current.get();
            if (sub.getStatus() == SubscriptionStatus.PAUSED) {
                throw new BusinessException("Tu suscripción está pausada. Habla con tu entrenador para reactivarla.");
            }
            if (sub.getPlan().getId().equals(plan.getId()) && sub.getBillingPeriod() == period) {
                if (sub.isCancelAtPeriodEnd()) {
                    return resumeMySubscription();
                }
                throw new BusinessException("Ya tienes contratado ese plan con esa modalidad de pago");
            }
        }
        // Special conditions are agreed with the trainer for a given plan: a self-service change drops them.
        Subscription created = startNewSubscription(client, plan, period, null, null);
        emailService.sendToClient(client.getId(), EmailType.SUBSCRIPTION, "Tu plan ha cambiado",
                emailTemplates.subscriptionChanged(client.getFirstName(), "Tu nuevo plan: " + plan.getName(),
                        "Tu suscripción al plan " + plan.getName() + " está activa desde hoy. "
                                + "Modalidad " + period.label() + ". Se renovará el "
                                + created.getRenewalDate().format(DATE) + "."));
        return subscriptionMapper.toResponse(created);
    }

    /** Cancels at the end of the paid period: no more charges, access until renewalDate. */
    @Transactional
    public SubscriptionResponse cancelMySubscription() {
        Client client = clientService.getCurrentClient();
        Subscription sub = getActiveOrThrow(client.getId());
        if (sub.isCancelAtPeriodEnd()) {
            throw new BusinessException("La suscripción ya está cancelada");
        }
        sub.setCancelAtPeriodEnd(true);
        sub.setCancelledAt(Instant.now());
        Subscription saved = subscriptionRepository.save(sub);
        emailService.sendToClient(client.getId(), EmailType.SUBSCRIPTION, "Cancelación confirmada",
                emailTemplates.subscriptionChanged(client.getFirstName(), "Hemos cancelado tu suscripción",
                        "Seguirás teniendo acceso hasta el " + sub.getRenewalDate().format(DATE)
                                + ". No se realizarán más cobros. Puedes reactivarla cuando quieras."));
        return subscriptionMapper.toResponse(saved);
    }

    @Transactional
    public SubscriptionResponse resumeMySubscription() {
        Subscription sub = getActiveOrThrow(clientService.getCurrentClientId());
        if (!sub.isCancelAtPeriodEnd()) {
            throw new BusinessException("La suscripción no está cancelada");
        }
        sub.setCancelAtPeriodEnd(false);
        sub.setCancelledAt(null);
        return subscriptionMapper.toResponse(subscriptionRepository.save(sub));
    }

    // ── Shared queries ──────────────────────────────────────────────────────

    public SubscriptionResponse getActiveByClientId(Long clientId) {
        clientService.assertCanAccess(clientId);
        return subscriptionMapper.toResponse(getActiveOrThrow(clientId));
    }

    public List<SubscriptionResponse> getHistoryByClientId(Long clientId) {
        clientService.assertCanAccess(clientId);
        return subscriptionRepository.findByClientIdOrderByStartDateDesc(clientId)
                .stream().map(subscriptionMapper::toResponse).toList();
    }

    public Optional<Subscription> findCurrent(Long clientId) {
        return subscriptionRepository.findByClientIdAndActiveTrue(clientId);
    }

    /** Plan of the client's current subscription, only while it is ACTIVE (not paused). */
    public Optional<Plan> findActivePlan(Long clientId) {
        return subscriptionRepository.findByClientIdAndActiveTrue(clientId)
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .map(Subscription::getPlan);
    }

    public boolean hasFeature(Long clientId, Predicate<Plan> feature) {
        return findActivePlan(clientId).map(feature::test).orElse(false);
    }

    public void requireFeature(Long clientId, Predicate<Plan> feature, String message) {
        if (!hasFeature(clientId, feature)) {
            throw new FeatureNotAvailableException(message);
        }
    }

    // ── Renewals (daily job) ────────────────────────────────────────────────

    /**
     * Rolls over every ACTIVE subscription whose period ended: either closes it (cancelled by the
     * client) or opens the next monthly period and publishes its charge.
     *
     * @return number of subscriptions processed
     */
    @Transactional
    public int processRenewals(LocalDate today) {
        List<Subscription> due = subscriptionRepository
                .findByStatusAndRenewalDateLessThanEqualOrderByRenewalDateAsc(SubscriptionStatus.ACTIVE, today);
        for (Subscription sub : due) {
            if (sub.isCancelAtPeriodEnd()) {
                close(sub, sub.getRenewalDate());
                subscriptionRepository.save(sub);
                Client client = clientService.getEntityById(sub.getClientId());
                emailService.sendToClient(client.getId(), EmailType.SUBSCRIPTION, "Tu suscripción ha finalizado",
                        emailTemplates.subscriptionChanged(client.getFirstName(), "Tu suscripción ha finalizado",
                                "Tu plan " + sub.getPlan().getName() + " ha terminado. ¡Te esperamos de vuelta cuando quieras!"));
                continue;
            }
            int renewals = 0;
            while (!sub.getRenewalDate().isAfter(today) && renewals < MAX_RENEWALS_PER_RUN) {
                LocalDate periodStart = sub.getRenewalDate();
                LocalDate periodEnd = periodStart.plusMonths(sub.getBillingPeriod().months());
                sub.setRenewalDate(periodEnd);
                events.publishEvent(new SubscriptionChargeEvent(sub.getClientId(), sub.getId(),
                        sub.getPlan().getName(), sub.effectivePrice(), periodStart, periodEnd));
                renewals++;
            }
            subscriptionRepository.save(sub);
        }
        if (!due.isEmpty()) {
            log.info("Processed {} subscription renewals", due.size());
        }
        return due.size();
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        subscriptionRepository.deleteAll(subscriptionRepository.findByClientIdOrderByStartDateDesc(event.clientId()));
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Subscription startNewSubscription(Client client, Plan plan, BillingPeriod period,
                                              java.math.BigDecimal customPrice, String customPriceNote) {
        if (customPrice == null && plan.priceFor(period) == null) {
            throw new BusinessException("El plan " + plan.getName() + " no tiene modalidad " + period.label());
        }
        LocalDate today = LocalDate.now();
        subscriptionRepository.findByClientIdAndActiveTrue(client.getId()).ifPresent(current -> {
            close(current, today);
            subscriptionRepository.save(current);
        });
        subscriptionRepository.flush();

        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .clientId(client.getId())
                .plan(plan)
                .startDate(today)
                .renewalDate(today.plusMonths(period.months()))
                .billingPeriod(period)
                .customPrice(customPrice)
                .customPriceNote(customPriceNote)
                .status(SubscriptionStatus.ACTIVE)
                .active(true)
                .build());

        events.publishEvent(new SubscriptionChargeEvent(client.getId(), subscription.getId(), plan.getName(),
                subscription.effectivePrice(), today, subscription.getRenewalDate()));
        return subscription;
    }

    private void close(Subscription sub, LocalDate endDate) {
        sub.setEndDate(endDate);
        sub.setActive(false);
        sub.setStatus(SubscriptionStatus.CANCELLED);
        if (sub.getCancelledAt() == null) {
            sub.setCancelledAt(Instant.now());
        }
        events.publishEvent(new SubscriptionClosedEvent(sub.getClientId(), sub.getId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Subscription getActiveOrThrow(Long clientId) {
        return subscriptionRepository.findByClientIdAndActiveTrue(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found for client: " + clientId));
    }
}
