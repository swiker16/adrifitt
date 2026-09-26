package com.adrifit.backend.dashboard.dto;

import com.adrifit.backend.client.dto.ClientResponse;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientDashboardResponse(
        ClientResponse profile,
        long totalReports,
        WeeklyReportResponse latestReport,
        SubscriptionResponse subscription,
        long pendingPayments,
        BigDecimal pendingAmount,
        boolean overduePayments,
        LocalDate nextReviewDate,
        long unreadMessages,
        boolean messagingEnabled,
        boolean pdfExportEnabled,
        long workoutLogsThisWeek,
        long totalWorkoutLogs,
        long totalPhotos,
        String activeWorkoutName,
        String activeDietName,
        boolean hasTestimonial,
        BigDecimal startWeight,
        BigDecimal currentWeight,
        /** Trainer videos / corrections the client has not seen yet. */
        long newVideos
) {
}
