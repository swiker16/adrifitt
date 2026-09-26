package com.adrifit.backend.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/** "Información general del negocio" for the trainer. Amounts in EUR. */
public record BusinessOverviewResponse(
        long totalClients,
        long activeSubscriptions,
        long pausedSubscriptions,
        long cancellationsLast30Days,
        /* Monthly recurring revenue: sum of the monthly price of ACTIVE subscriptions. */
        BigDecimal monthlyRecurringRevenue,
        BigDecimal revenueThisMonth,
        BigDecimal revenueLastMonth,
        BigDecimal revenueLast12Months,
        BigDecimal refundedLast12Months,
        BigDecimal pendingAmount,
        BigDecimal overdueAmount,
        BigDecimal averageRevenuePerActiveClient,
        /* Cancellations in the last 30 days / active subscriptions at the start of that window (%). */
        BigDecimal churnRatePercent,
        List<MonthAmount> revenueByMonth,
        List<MethodAmount> revenueByMethod,
        List<PlanBreakdown> plans,
        List<MonthCount> newClientsByMonth
) {

    public record MonthAmount(String month, BigDecimal amount, long payments) {}

    public record MethodAmount(String method, BigDecimal amount, long payments) {}

    public record PlanBreakdown(Long planId, String planName, BigDecimal monthlyPrice, long activeClients, BigDecimal mrr) {}

    public record MonthCount(String month, long count) {}
}
