package com.adrifit.backend.report.event;

/** The trainer gave feedback on a client's report (i.e. the periodic review was done). */
public record ReportReviewedEvent(Long clientId, Long reportId) {
}
