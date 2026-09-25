package com.adrifit.backend.email.domain;

public enum EmailStatus {
    /** Waiting for the surrounding transaction to commit. */
    QUEUED,
    /** Test mode: captured in the outbox, not delivered. */
    TEST_CAPTURED,
    SENT,
    FAILED
}
