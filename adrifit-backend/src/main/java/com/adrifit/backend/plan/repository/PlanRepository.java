package com.adrifit.backend.plan.repository;

import com.adrifit.backend.plan.domain.Plan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByActiveTrueOrderByMonthlyPriceAsc();

    List<Plan> findAllByOrderByMonthlyPriceAsc();

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT COUNT(s) FROM com.adrifit.backend.subscription.domain.Subscription s WHERE s.plan.id = :planId AND s.active = true")
    long countActiveSubscriptionsByPlanId(Long planId);

    @Query("SELECT COUNT(s) FROM com.adrifit.backend.subscription.domain.Subscription s WHERE s.plan.id = :planId")
    long countSubscriptionsByPlanId(Long planId);
}
