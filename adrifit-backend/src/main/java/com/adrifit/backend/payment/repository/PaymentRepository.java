package com.adrifit.backend.payment.repository;

import com.adrifit.backend.payment.domain.Payment;
import com.adrifit.backend.payment.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findAllByOrderByDueDateDescIdDesc();

    List<Payment> findByStatusOrderByDueDateAscIdAsc(PaymentStatus status);

    List<Payment> findByClientIdOrderByDueDateDescIdDesc(Long clientId);

    List<Payment> findBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);

    List<Payment> findByClientIdAndStatus(Long clientId, PaymentStatus status);

    List<Payment> findByStatusAndPaidAtBetween(PaymentStatus status, Instant from, Instant to);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByClientId(Long clientId);
}
