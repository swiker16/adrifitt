package com.adrifit.backend.common.exception;

/**
 * The client's current plan does not include the requested feature (messaging, PDF export...).
 * Mapped to HTTP 403 with a human readable message.
 */
public class FeatureNotAvailableException extends RuntimeException {

    public FeatureNotAvailableException(String message) {
        super(message);
    }
}
