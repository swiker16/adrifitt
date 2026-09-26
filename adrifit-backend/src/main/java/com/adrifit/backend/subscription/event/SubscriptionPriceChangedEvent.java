package com.adrifit.backend.subscription.event;

import java.math.BigDecimal;

/** The special price of a subscription changed: unpaid charges of that subscription follow it. */
public record SubscriptionPriceChangedEvent(Long subscriptionId, BigDecimal newAmount) {
}
