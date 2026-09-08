package com.adrifit.backend.dashboard.dto;

import com.adrifit.backend.client.dto.ClientResponse;
import com.adrifit.backend.report.dto.WeeklyReportResponse;

public record ClientDashboardResponse(
        ClientResponse profile,
        long totalReports,
        WeeklyReportResponse latestReport
) {
}
