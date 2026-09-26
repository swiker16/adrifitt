package com.adrifit.backend.report.dto;

import java.math.BigDecimal;

/** Client check-in: weight + optional comment (+ 4-6 photos sent as multipart files). */
public record SubmitReportRequest(BigDecimal weight, String comments) {
}
