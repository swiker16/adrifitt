package com.adrifit.backend.lead.domain;

/** Intake pipeline of a prospective client. */
public enum LeadStatus {
    /** Contact form received: the trainer decides whether to send the questionnaire. */
    NEW,
    /** Questionnaire link emailed, waiting for the person to fill it in. */
    QUESTIONNAIRE_SENT,
    /** Questionnaire answered: the trainer reviews it and accepts or rejects. */
    QUESTIONNAIRE_COMPLETED,
    /** Account created and activation email sent. */
    ACCEPTED,
    /** Declined (after the request or after the questionnaire). */
    REJECTED
}
