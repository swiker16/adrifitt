package com.adrifit.backend.plan.mapper;

import com.adrifit.backend.plan.domain.BillingPeriod;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.dto.PlanResponse;
import com.adrifit.backend.plan.dto.PlanResponse.PeriodPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlanMapper {

    public PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getMonthlyPrice(),
                plan.getQuarterlyPrice(),
                plan.getSemiannualPrice(),
                plan.getAnnualPrice(),
                prices(plan),
                plan.featureList(),
                plan.getReviewFrequencyDays(),
                plan.isMessagingEnabled(),
                plan.isAnalyticsEnabled(),
                plan.isPdfExportEnabled(),
                plan.isPrioritySupport(),
                plan.isActive(),
                plan.getCreatedAt()
        );
    }

    private List<PeriodPrice> prices(Plan plan) {
        List<PeriodPrice> list = new ArrayList<>();
        BigDecimal monthly = plan.getMonthlyPrice();
        for (BillingPeriod period : BillingPeriod.values()) {
            BigDecimal price = plan.priceFor(period);
            if (price == null) {
                continue;
            }
            BigDecimal perMonth = price.divide(BigDecimal.valueOf(period.months()), 2, RoundingMode.HALF_UP);
            int saving = 0;
            if (monthly != null && monthly.signum() > 0 && period != BillingPeriod.MONTHLY) {
                BigDecimal full = monthly.multiply(BigDecimal.valueOf(period.months()));
                saving = full.subtract(price).multiply(BigDecimal.valueOf(100))
                        .divide(full, 0, RoundingMode.HALF_UP).max(BigDecimal.ZERO).intValue();
            }
            list.add(new PeriodPrice(period, period.months(), price, perMonth, saving));
        }
        return list;
    }
}
