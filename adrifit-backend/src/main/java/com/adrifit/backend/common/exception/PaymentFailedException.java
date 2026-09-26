package com.adrifit.backend.common.exception;

/**
 * The payment provider rejected a charge (declined card, rejected Bizum...). Mapped to HTTP 402.
 */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
