package com.adrifit.backend.email.service;

/**
 * Delivery channel for emails. Selected with {@code adrifit.mail.mode} (test | smtp).
 */
public interface EmailSender {

    /**
     * Delivers the email. Throws on failure.
     */
    void deliver(String from, String to, String subject, String htmlBody);

    /** "test" or "smtp". */
    String mode();
}
