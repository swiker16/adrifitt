package com.adrifit.backend.lead.dto;

import com.adrifit.backend.lead.domain.LeadStatus;
import com.adrifit.backend.plan.domain.BillingPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class LeadDtos {

    private LeadDtos() {
    }

    /** Public "Quiero empezar" form. {@code website} is a honeypot: humans never fill it. */
    public record ContactRequest(
            @NotBlank(message = "Escribe tu nombre") @Size(max = 100) String firstName,
            @NotBlank(message = "Escribe tus apellidos") @Size(max = 100) String lastName,
            @NotBlank(message = "Escribe tu email") @Email(message = "El email no es válido") @Size(max = 320) String email,
            @Size(max = 30) String phone,
            @NotBlank(message = "Cuéntanos brevemente tu objetivo")
            @Size(min = 10, max = 1500, message = "Cuéntanos tu objetivo en al menos 10 caracteres")
            String objective,
            Long planId,
            @AssertTrue(message = "Debes aceptar la política de privacidad") boolean consent,
            String website
    ) {
    }

    public record LeadSummary(
            Long id,
            String firstName,
            String lastName,
            String email,
            String phone,
            String objective,
            Long preferredPlanId,
            String preferredPlanName,
            LeadStatus status,
            Instant createdAt,
            Instant questionnaireSentAt,
            Instant questionnaireCompletedAt,
            Instant decidedAt,
            Long clientId,
            /** Health warning in the questionnaire (injuries or medical conditions). */
            boolean healthFlag
    ) {
    }

    public record LeadDetail(
            LeadSummary lead,
            Questionnaire questionnaire,
            String questionnairePlanName,
            boolean questionnaireExpired,
            Instant questionnaireExpiresAt,
            String trainerNote,
            String decisionMessage,
            /** Accepted but the person has not activated the account yet. */
            boolean activationPending
    ) {
    }

    public record LeadCounts(long newRequests, long questionnaireSent, long toReview, long accepted, long rejected) {
    }

    /** Optional personal message included in the email sent with a decision. */
    public record DecisionRequest(@Size(max = 2000) String message) {
    }

    public record NoteRequest(@Size(max = 2000) String note) {
    }

    public record ApproveRequest(
            @NotNull(message = "Elige el plan") Long planId,
            BillingPeriod billingPeriod,
            @DecimalMin(value = "0.0", message = "El precio especial no puede ser negativo") BigDecimal customPrice,
            @Size(max = 200) String customPriceNote,
            @Size(max = 2000) String message
    ) {
    }

    /** What the public questionnaire page needs to know about the link. */
    public record QuestionnaireInfo(
            String firstName,
            /** OPEN, EXPIRED, COMPLETED or CLOSED (accepted/rejected/invalid). */
            String state,
            Instant expiresAt,
            Long preferredPlanId
    ) {
    }

    public record ActivationInfo(String firstName, String username, String email) {
    }

    public record ActivateRequest(
            @NotBlank @Size(min = 8, max = 100, message = "La contraseña debe tener al menos 8 caracteres") String password
    ) {
    }
}
