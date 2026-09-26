package com.adrifit.backend.subscription.repository;

import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByClientIdAndActiveTrue(Long clientId);

    List<Subscription> findByClientIdOrderByStartDateDesc(Long clientId);

    List<Subscription> findByStatus(SubscriptionStatus status);

    long countByStatus(SubscriptionStatus status);

    long countByStatusAndRenewalDateLessThanEqual(SubscriptionStatus status, LocalDate date);

    long countByStatusAndRenewalDateBetween(SubscriptionStatus status, LocalDate from, LocalDate to);

    List<Subscription> findByStatusAndRenewalDateBetweenOrderByRenewalDateAsc(SubscriptionStatus status, LocalDate from, LocalDate to);

    List<Subscription> findByStatusAndRenewalDateLessThanEqualOrderByRenewalDateAsc(SubscriptionStatus status, LocalDate date);

    List<Subscription> findByActiveTrue();

    long countByStatusAndEndDateBetween(SubscriptionStatus status, LocalDate from, LocalDate to);
}
