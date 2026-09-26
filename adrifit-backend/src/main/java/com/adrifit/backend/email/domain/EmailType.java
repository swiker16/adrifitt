package com.adrifit.backend.email.domain;

public enum EmailType {
    WELCOME,
    PASSWORD_RESET,
    PAYMENT_DUE,
    PAYMENT_RECEIPT,
    SUBSCRIPTION,
    REPORT_FEEDBACK,
    REVIEW_REMINDER,
    CUSTOM,
    /** New client intake (contact request, questionnaire, decision, activation). */
    LEAD_RECEIVED,
    LEAD_NOTIFICATION,
    QUESTIONNAIRE_INVITE,
    LEAD_REJECTED,
    ACCOUNT_ACTIVATION
}
