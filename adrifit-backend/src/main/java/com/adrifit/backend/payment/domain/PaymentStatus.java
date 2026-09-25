package com.adrifit.backend.payment.domain;

public enum PaymentStatus {
    /** Waiting to be paid (failed attempts keep it pending). */
    PENDING,
    PAID,
    REFUNDED,
    /** Voided: plan changed/cancelled before it was paid, or cancelled by the trainer. */
    CANCELLED
}
