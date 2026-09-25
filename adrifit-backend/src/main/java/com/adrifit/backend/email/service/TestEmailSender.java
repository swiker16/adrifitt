package com.adrifit.backend.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Test mode: nothing leaves the server. The email is only logged; its full content stays in the
 * outbox table, where the trainer can read it from the "Emails" section.
 */
@Component
@ConditionalOnProperty(name = "adrifit.mail.mode", havingValue = "test", matchIfMissing = true)
public class TestEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(TestEmailSender.class);

    @Override
    public void deliver(String from, String to, String subject, String htmlBody) {
        log.info("[MAIL TEST MODE] to={} subject=\"{}\" (not sent, stored in outbox)", to, subject);
    }

    @Override
    public String mode() {
        return "test";
    }
}
