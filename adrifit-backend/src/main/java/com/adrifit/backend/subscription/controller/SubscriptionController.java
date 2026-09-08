package com.adrifit.backend.subscription.controller;

import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import com.adrifit.backend.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PutMapping("/api/clients/{clientId}/subscription")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<SubscriptionResponse> assignPlan(@PathVariable Long clientId,
                                                           @Valid @RequestBody AssignPlanRequest request) {
        return ResponseEntity.ok(subscriptionService.assignPlan(clientId, request));
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

    @GetMapping("/api/subscriptions/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<SubscriptionResponse> getMySubscription() {
        return ResponseEntity.ok(subscriptionService.getMySubscription());
    }
}
