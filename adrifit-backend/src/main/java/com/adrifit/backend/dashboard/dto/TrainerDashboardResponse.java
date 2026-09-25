package com.adrifit.backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TrainerDashboardResponse(
        // ── Métricas ──
        long totalClients,
        long activeClients,
        long totalReports,
        long reviewsPending,
        long reviewsThisWeek,
        long reportsThisWeek,
        long reportsPendingFeedback,
        long newClientsThisMonth,
        long clientsWithoutWorkout,
        long upcomingRenewalsCount,
        long pendingAnalyses,
        long unreadMessages,
        long pendingPaymentsCount,
        BigDecimal pendingPaymentsAmount,
        long overduePaymentsCount,
        long tasksDue,
        BigDecimal revenueThisMonth,
        BigDecimal monthlyRecurringRevenue,

        // ── Distribución por plan ──
        List<PlanDistribution> clientsPerPlan,

        // ── Secciones accionables ──
        List<PendingReview> pendingReviews,
        List<ReportWithoutFeedback> reportsWithoutFeedback,
        List<ClientWithoutWorkout> clientsWithoutWorkouts,
        List<UpcomingRenewal> upcomingRenewals,

        // ── Actividad reciente ──
        List<ActivityItem> recentActivity
) {

    public record PlanDistribution(Long planId, String planName, long clients) {}

    public record PendingReview(
            Long clientId,
            String clientFirstName,
            String clientLastName,
            String planName,
            LocalDate renewalDate,
            LocalDate nextReviewDate,
            long daysSinceLastReview
    ) {}

    public record ReportWithoutFeedback(
            Long reportId,
            Long clientId,
            String clientFirstName,
            String clientLastName,
            Instant reportDate,
            BigDecimal weight
    ) {}

    public record ClientWithoutWorkout(
            Long clientId,
            String clientFirstName,
            String clientLastName,
            String planName,
            String lastWorkoutName
    ) {}

    public record UpcomingRenewal(
            Long clientId,
            String clientFirstName,
            String clientLastName,
            String planName,
            BigDecimal amount,
            LocalDate renewalDate,
            long daysUntilRenewal
    ) {}

    public record ActivityItem(
            String type,
            Long clientId,
            String clientFirstName,
            String clientLastName,
            String description,
            Instant occurredAt
    ) {}
}
