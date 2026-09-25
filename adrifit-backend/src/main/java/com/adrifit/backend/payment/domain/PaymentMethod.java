package com.adrifit.backend.payment.domain;

public enum PaymentMethod {
    CARD,
    BIZUM,
    /** Only the trainer can register cash payments. */
    CASH
}
