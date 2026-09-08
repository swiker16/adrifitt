package com.adrifit.backend.report.repository;

import com.adrifit.backend.report.domain.ReportStatus;
import com.adrifit.backend.report.domain.WeeklyReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    List<WeeklyReport> findByClient_IdOrderByCreatedAtDesc(Long clientId);

    Optional<WeeklyReport> findTopByClient_IdOrderByCreatedAtDesc(Long clientId);

    long countByClient_Id(Long clientId);

    long countByCreatedAtBetween(Instant from, Instant to);

    List<WeeklyReport> findTop5ByOrderByCreatedAtDesc();

    List<WeeklyReport> findAllByOrderByCreatedAtDesc();

    List<WeeklyReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<WeeklyReport> findByCoachFeedbackIsNullOrderByCreatedAtDesc();

    List<WeeklyReport> findByClient_IdAndCreatedAtBetween(Long clientId, Instant from, Instant to);
}
