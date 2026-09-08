package com.adrifit.backend.dashboard.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.mapper.ClientMapper;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.dashboard.dto.ClientDashboardResponse;
import com.adrifit.backend.dashboard.dto.TrainerDashboardResponse;
import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import com.adrifit.backend.report.repository.WeeklyReportRepository;
import com.adrifit.backend.report.service.WeeklyReportService;
import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.service.UserService;
import com.adrifit.backend.workout.domain.ClientWorkout;
import com.adrifit.backend.workout.repository.ClientWorkoutRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final ClientMapper clientMapper;
    private final WeeklyReportService reportService;
    private final WeeklyReportRepository weeklyReportRepository;
    private final UserService userService;
    private final SubscriptionRepository subscriptionRepository;
    private final ClientWorkoutRepository clientWorkoutRepository;

    public DashboardService(ClientRepository clientRepository,
                            ClientService clientService,
                            ClientMapper clientMapper,
                            WeeklyReportService reportService,
                            WeeklyReportRepository weeklyReportRepository,
                            UserService userService,
                            SubscriptionRepository subscriptionRepository,
                            ClientWorkoutRepository clientWorkoutRepository) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.clientMapper = clientMapper;
        this.reportService = reportService;
        this.weeklyReportRepository = weeklyReportRepository;
        this.userService = userService;
        this.subscriptionRepository = subscriptionRepository;
        this.clientWorkoutRepository = clientWorkoutRepository;
    }

    public TrainerDashboardResponse getTrainerDashboard() {
        LocalDate today = LocalDate.now();
        Instant weekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant weekEnd = weekStart.plusSeconds(7 * 24 * 3600);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant now = Instant.now();

        long totalClients = clientRepository.count();
        long totalReports = weeklyReportRepository.count();
        long activeClients = subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE);
        long reviewsPending = subscriptionRepository
                .countByStatusAndRenewalDateLessThanEqual(SubscriptionStatus.ACTIVE, today);
        long reviewsThisWeek = subscriptionRepository
                .countByStatusAndRenewalDateBetween(SubscriptionStatus.ACTIVE, today, today.plusDays(7));
        long reportsThisWeek = weeklyReportRepository.countByCreatedAtBetween(weekStart, weekEnd);
        long newClientsThisMonth = clientRepository.countByCreatedAtBetween(monthStart, now);

        // ── Reportes sin feedback ──
        List<WeeklyReport> noFeedbackReports = weeklyReportRepository.findByCoachFeedbackIsNullOrderByCreatedAtDesc();
        long reportsPendingFeedback = noFeedbackReports.size();
        List<TrainerDashboardResponse.ReportWithoutFeedback> reportsWithoutFeedback = noFeedbackReports.stream()
                .limit(10)
                .map(r -> new TrainerDashboardResponse.ReportWithoutFeedback(
                        r.getId(), r.getClient().getId(),
                        r.getClient().getFirstName(), r.getClient().getLastName(),
                        r.getCreatedAt(), r.getWeight()))
                .toList();

        // ── Clientes sin rutina activa (con suscripción activa) ──
        Set<Long> clientsWithActiveWorkout = clientWorkoutRepository.findAll().stream()
                .filter(ClientWorkout::isActive)
                .map(ClientWorkout::getClientId)
                .collect(Collectors.toSet());

        List<Subscription> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
        List<TrainerDashboardResponse.ClientWithoutWorkout> clientsWithoutWorkouts = activeSubs.stream()
                .filter(s -> !clientsWithActiveWorkout.contains(s.getClientId()))
                .limit(10)
                .map(s -> {
                    Client c = clientRepository.findById(s.getClientId()).orElse(null);
                    List<ClientWorkout> history = clientWorkoutRepository.findAllByClientIdOrderByAssignedAtDesc(s.getClientId());
                    String lastWorkout = history.isEmpty() ? null : history.get(0).getWorkout().getName();
                    return new TrainerDashboardResponse.ClientWithoutWorkout(
                            s.getClientId(),
                            c != null ? c.getFirstName() : "—",
                            c != null ? c.getLastName() : "",
                            s.getPlan().getName(),
                            lastWorkout);
                })
                .toList();

        long clientsWithoutWorkout = clientsWithoutWorkouts.size();

        // ── Revisiones (basadas en último reporte enviado por el cliente) ──
        List<TrainerDashboardResponse.PendingReview> pendingReviews = activeSubs.stream()
                .map(s -> {
                    Client c = clientRepository.findById(s.getClientId()).orElse(null);
                    int freqDays = s.getPlan().getReviewFrequencyDays();
                    // Fecha de referencia: último reporte o, si no hay, startDate de la suscripción
                    LocalDate lastReportDate = weeklyReportRepository
                            .findTopByClient_IdOrderByCreatedAtDesc(s.getClientId())
                            .map(r -> r.getCreatedAt().atZone(java.time.ZoneOffset.UTC).toLocalDate())
                            .orElse(s.getStartDate());
                    LocalDate nextReviewDate = lastReportDate.plusDays(freqDays);
                    long daysSince = ChronoUnit.DAYS.between(lastReportDate, today);
                    return new TrainerDashboardResponse.PendingReview(
                            s.getClientId(),
                            c != null ? c.getFirstName() : "—",
                            c != null ? c.getLastName() : "",
                            s.getPlan().getName(),
                            s.getRenewalDate(),
                            nextReviewDate,
                            Math.max(0, daysSince));
                })
                .sorted(java.util.Comparator.comparing(TrainerDashboardResponse.PendingReview::nextReviewDate))
                .toList();

        // ── Próximos cobros (30 días) ──
        List<TrainerDashboardResponse.UpcomingRenewal> upcomingRenewals = subscriptionRepository
                .findByStatusAndRenewalDateBetweenOrderByRenewalDateAsc(SubscriptionStatus.ACTIVE, today, today.plusDays(30))
                .stream()
                .limit(10)
                .map(s -> {
                    Client c = clientRepository.findById(s.getClientId()).orElse(null);
                    long days = ChronoUnit.DAYS.between(today, s.getRenewalDate());
                    return new TrainerDashboardResponse.UpcomingRenewal(
                            s.getClientId(),
                            c != null ? c.getFirstName() : "—",
                            c != null ? c.getLastName() : "",
                            s.getPlan().getName(),
                            s.getPlan().getMonthlyPrice(),
                            s.getRenewalDate(),
                            days);
                })
                .toList();

        long upcomingRenewalsCount = upcomingRenewals.size();

        // ── Distribución por plan ──
        Map<Long, List<Subscription>> grouped = activeSubs.stream()
                .collect(Collectors.groupingBy(s -> s.getPlan().getId()));
        List<TrainerDashboardResponse.PlanDistribution> clientsPerPlan = grouped.values().stream()
                .map(subs -> new TrainerDashboardResponse.PlanDistribution(
                        subs.get(0).getPlan().getId(),
                        subs.get(0).getPlan().getName(),
                        subs.size()))
                .sorted((a, b) -> Long.compare(b.clients(), a.clients()))
                .toList();

        // ── Actividad reciente (mezcla de eventos) ──
        List<TrainerDashboardResponse.ActivityItem> recentActivity = new ArrayList<>();

        clientRepository.findTop5ByOrderByCreatedAtDesc().forEach(c ->
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "CLIENT_CREATED", c.getId(), c.getFirstName(), c.getLastName(),
                        "Nuevo cliente registrado", c.getCreatedAt())));

        weeklyReportRepository.findTop5ByOrderByCreatedAtDesc().forEach(r ->
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "REPORT_SUBMITTED", r.getClient().getId(),
                        r.getClient().getFirstName(), r.getClient().getLastName(),
                        "Reporte semanal enviado", r.getCreatedAt())));

        clientWorkoutRepository.findTop10ByOrderByAssignedAtDesc().stream().limit(5).forEach(cw -> {
            Client c = clientRepository.findById(cw.getClientId()).orElse(null);
            if (c != null) {
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "WORKOUT_ASSIGNED", c.getId(), c.getFirstName(), c.getLastName(),
                        "Rutina asignada: " + cw.getWorkout().getName(), cw.getAssignedAt()));
            }
        });

        recentActivity.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));

        return new TrainerDashboardResponse(
                totalClients, activeClients, totalReports,
                reviewsPending, reviewsThisWeek, reportsThisWeek,
                reportsPendingFeedback, newClientsThisMonth,
                clientsWithoutWorkout, upcomingRenewalsCount,
                clientsPerPlan,
                pendingReviews, reportsWithoutFeedback, clientsWithoutWorkouts, upcomingRenewals,
                recentActivity
        );
    }

    public ClientDashboardResponse getClientDashboard() {
        User currentUser = userService.getCurrentUser();
        Client client = clientService.getByUserId(currentUser.getId());

        List<WeeklyReportResponse> reports = reportService.findByClientId(client.getId());
        WeeklyReportResponse latest = reports.isEmpty() ? null : reports.get(0);

        return new ClientDashboardResponse(
                clientMapper.toResponse(client),
                reports.size(),
                latest
        );
    }
}
