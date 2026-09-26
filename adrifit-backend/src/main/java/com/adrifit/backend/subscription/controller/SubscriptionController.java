package com.adrifit.backend.subscription.controller;

import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.dto.ChangePlanRequest;
import com.adrifit.backend.subscription.dto.UpdatePricingRequest;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import com.adrifit.backend.subscription.dto.UpdateSubscriptionStatusRequest;
import com.adrifit.backend.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    @PutMapping("/api/clients/{clientId}/subscription")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<SubscriptionResponse> assignPlan(@PathVariable Long clientId,
                                                           @Valid @RequestBody AssignPlanRequest request) {
        return ResponseEntity.ok(subscriptionService.assignPlan(clientId, request));
    }

    @PatchMapping("/api/clients/{clientId}/subscription/status")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<SubscriptionResponse> updateStatus(@PathVariable Long clientId,
                                                             @Valid @RequestBody UpdateSubscriptionStatusRequest request) {
        return ResponseEntity.ok(subscriptionService.updateStatus(clientId, request.status()));
    }

    @PatchMapping("/api/clients/{clientId}/subscription/pricing")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<SubscriptionResponse> updatePricing(@PathVariable Long clientId,
                                                              @Valid @RequestBody UpdatePricingRequest request) {
        return ResponseEntity.ok(subscriptionService.updatePricing(clientId, request));
    }

    @GetMapping("/api/clients/{clientId}/subscription")
    @PreAuthorize("hasAnyRole('TRAINER','CLIENT')")
    public ResponseEntity<SubscriptionResponse> getActive(@PathVariable Long clientId) {
        return ResponseEntity.ok(subscriptionService.getActiveByClientId(clientId));
    }

    @GetMapping("/api/clients/{clientId}/subscription/history")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<SubscriptionResponse>> getHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(subscriptionService.getHistoryByClientId(clientId));
    }

    // ── Client ──────────────────────────────────────────────────────────────

    @GetMapping("/api/subscriptions/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<SubscriptionResponse> getMySubscription() {
        return ResponseEntity.ok(subscriptionService.getMySubscription());
    }

    @GetMapping("/api/subscriptions/me/history")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<SubscriptionResponse>> getMyHistory() {
        return ResponseEntity.ok(subscriptionService.getMyHistory());
    }

    @PostMapping("/api/subscriptions/me/change-plan")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<SubscriptionResponse> changePlan(@Valid @RequestBody ChangePlanRequest request) {
        return ResponseEntity.ok(subscriptionService.changeMyPlan(request.planId(), request.billingPeriod()));
    }

    @PostMapping("/api/subscriptions/me/cancel")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<SubscriptionResponse> cancel() {
        return ResponseEntity.ok(subscriptionService.cancelMySubscription());
    }

    @PostMapping("/api/subscriptions/me/resume")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<SubscriptionResponse> resume() {
        return ResponseEntity.ok(subscriptionService.resumeMySubscription());
    }
}
