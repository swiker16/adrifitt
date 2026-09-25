package com.adrifit.backend.email.dto;

import com.adrifit.backend.email.domain.EmailStatus;
import com.adrifit.backend.email.domain.EmailType;
import java.time.Instant;

public record EmailResponse(
        Long id,
        Long clientId,
        String toAddress,
        String toName,
        String subject,
        String htmlBody,
        EmailType type,
        EmailStatus status,
        String mode,
        String errorMessage,
        Instant createdAt,
        Instant sentAt
) {
}
