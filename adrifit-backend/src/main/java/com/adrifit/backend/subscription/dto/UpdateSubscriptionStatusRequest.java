package com.adrifit.backend.subscription.dto;

import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSubscriptionStatusRequest(
        @NotNull(message = "Status is required")
        SubscriptionStatus status
) {
}
