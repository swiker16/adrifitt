package com.adrifit.backend.subscription.event;

/**
 * A subscription stopped being the client's current one (plan change or cancellation).
 * Unpaid charges of that subscription are voided by the payment module.
 */
public record SubscriptionClosedEvent(Long clientId, Long subscriptionId) {
}
