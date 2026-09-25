package com.adrifit.backend.subscription.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A subscription period must be paid (new subscription or renewal). The payment module listens
 * and creates the pending charge.
 */
public record SubscriptionChargeEvent(
        Long clientId,
        Long subscriptionId,
        String planName,
        BigDecimal amount,
        LocalDate periodStart,
        LocalDate periodEnd
) {
}
