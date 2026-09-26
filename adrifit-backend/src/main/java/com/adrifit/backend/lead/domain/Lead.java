package com.adrifit.backend.lead.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** A person who asked to train with the trainer (not a client yet). */
@Entity
@Table(name = "leads")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, length = 1500)
    private String objective;

    @Column(name = "preferred_plan_id")
    private Long preferredPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadStatus status;

    @Column(name = "questionnaire_token_hash", length = 64, unique = true)
    private String questionnaireTokenHash;

    @Column(name = "questionnaire_sent_at")
    private Instant questionnaireSentAt;

    @Column(name = "questionnaire_expires_at")
    private Instant questionnaireExpiresAt;

    @Column(name = "questionnaire_json", columnDefinition = "TEXT")
    private String questionnaireJson;

    @Column(name = "questionnaire_completed_at")
    private Instant questionnaireCompletedAt;

    @Column(name = "trainer_note", length = 2000)
    private String trainerNote;

    @Column(name = "decision_message", length = 2000)
    private String decisionMessage;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "health_consent_at")
    private Instant healthConsentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
