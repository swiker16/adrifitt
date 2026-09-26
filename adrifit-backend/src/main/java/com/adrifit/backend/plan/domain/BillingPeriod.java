package com.adrifit.backend.plan.domain;

/** How often a subscription is charged (and renewed). */
public enum BillingPeriod {
    MONTHLY(1, "mensual"),
    QUARTERLY(3, "trimestral"),
    SEMIANNUAL(6, "semestral"),
    ANNUAL(12, "anual");

    private final int months;
    private final String label;

    BillingPeriod(int months, String label) {
        this.months = months;
        this.label = label;
    }

    public int months() {
        return months;
    }

    public String label() {
        return label;
    }
}
