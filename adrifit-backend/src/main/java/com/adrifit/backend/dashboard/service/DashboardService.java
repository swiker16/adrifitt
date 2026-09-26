package com.adrifit.backend.dashboard.service;

import com.adrifit.backend.video.service.TechniqueVideoService;
import com.adrifit.backend.analysis.service.AnalysisService;
import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.mapper.ClientMapper;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.dashboard.dto.BusinessOverviewResponse;
import com.adrifit.backend.dashboard.dto.BusinessOverviewResponse.MethodAmount;
import com.adrifit.backend.dashboard.dto.BusinessOverviewResponse.MonthAmount;
import com.adrifit.backend.dashboard.dto.BusinessOverviewResponse.MonthCount;
import com.adrifit.backend.dashboard.dto.BusinessOverviewResponse.PlanBreakdown;
import com.adrifit.backend.dashboard.dto.ClientDashboardResponse;
import com.adrifit.backend.dashboard.dto.TrainerDashboardResponse;
import com.adrifit.backend.diet.repository.ClientDietRepository;
import com.adrifit.backend.message.service.MessageService;
import com.adrifit.backend.payment.domain.Payment;
import com.adrifit.backend.payment.domain.PaymentMethod;
import com.adrifit.backend.payment.domain.PaymentStatus;
import com.adrifit.backend.payment.repository.PaymentRepository;
import com.adrifit.backend.photo.service.ProgressPhotoService;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.dto.WeeklyReportResponse;
import com.adrifit.backend.report.repository.WeeklyReportRepository;
import com.adrifit.backend.report.service.WeeklyReportService;
import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import com.adrifit.backend.subscription.mapper.SubscriptionMapper;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import com.adrifit.backend.task.dto.TaskDtos.ReviewScheduleItem;
import com.adrifit.backend.task.service.TaskService;
import com.adrifit.backend.testimonial.repository.TestimonialRepository;
import com.adrifit.backend.workout.domain.ClientWorkout;
import com.adrifit.backend.workout.repository.ClientWorkoutRepository;
import com.adrifit.backend.workoutlog.repository.WorkoutLogRepository;
import com.adrifit.backend.workoutlog.service.WorkoutLogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final ClientMapper clientMapper;
    private final WeeklyReportService reportService;
    private final WeeklyReportRepository weeklyReportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final ClientWorkoutRepository clientWorkoutRepository;
    private final ClientDietRepository clientDietRepository;
    private final PaymentRepository paymentRepository;
    private final MessageService messageService;
    private final AnalysisService analysisService;
    private final TaskService taskService;
    private final WorkoutLogRepository workoutLogRepository;
    private final WorkoutLogService workoutLogService;
    private final ProgressPhotoService photoService;
    private final TestimonialRepository testimonialRepository;
    private final TechniqueVideoService videoService;

    public DashboardService(ClientRepository clientRepository,
                            ClientService clientService,
                            ClientMapper clientMapper,
                            WeeklyReportService reportService,
                            WeeklyReportRepository weeklyReportRepository,
                            SubscriptionRepository subscriptionRepository,
                            SubscriptionMapper subscriptionMapper,
                            ClientWorkoutRepository clientWorkoutRepository,
                            ClientDietRepository clientDietRepository,
                            PaymentRepository paymentRepository,
                            MessageService messageService,
                            AnalysisService analysisService,
                            TaskService taskService,
                            WorkoutLogRepository workoutLogRepository,
                            WorkoutLogService workoutLogService,
                            ProgressPhotoService photoService,
                            TestimonialRepository testimonialRepository,
                            TechniqueVideoService videoService) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.clientMapper = clientMapper;
        this.reportService = reportService;
        this.weeklyReportRepository = weeklyReportRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionMapper = subscriptionMapper;
        this.clientWorkoutRepository = clientWorkoutRepository;
        this.clientDietRepository = clientDietRepository;
        this.paymentRepository = paymentRepository;
        this.messageService = messageService;
        this.analysisService = analysisService;
        this.taskService = taskService;
        this.workoutLogRepository = workoutLogRepository;
        this.workoutLogService = workoutLogService;
        this.photoService = photoService;
        this.testimonialRepository = testimonialRepository;
        this.videoService = videoService;
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    public TrainerDashboardResponse getTrainerDashboard() {
        LocalDate today = LocalDate.now();
        Instant weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE).toInstant();
        Instant weekEnd = weekStart.plus(7, ChronoUnit.DAYS);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        Instant now = Instant.now();

        Map<Long, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        List<Subscription> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);

        long totalClients = clients.size();
        long totalReports = weeklyReportRepository.count();
        long activeClients = activeSubs.size();
        long reportsThisWeek = weeklyReportRepository.countByCreatedAtBetween(weekStart, weekEnd);
        long newClientsThisMonth = clientRepository.countByCreatedAtBetween(monthStart, now);

        // ── Revisiones (calendario de revisiones según el plan) ──
        List<ReviewScheduleItem> schedule = taskService.reviewSchedule();
        long reviewsPending = schedule.stream().filter(r -> !r.nextReviewDate().isAfter(today)).count();
        long reviewsThisWeek = schedule.stream()
                .filter(r -> !r.nextReviewDate().isBefore(today) && !r.nextReviewDate().isAfter(today.plusDays(7))).count();
        Map<Long, Subscription> subByClient = activeSubs.stream()
                .collect(Collectors.toMap(Subscription::getClientId, Function.identity(), (a, b) -> a));
        List<TrainerDashboardResponse.PendingReview> pendingReviews = schedule.stream()
                .map(r -> {
                    Client c = clients.get(r.clientId());
                    Subscription s = subByClient.get(r.clientId());
                    LocalDate reference = r.lastReviewDate() != null ? r.lastReviewDate() : (s != null ? s.getStartDate() : today);
                    return new TrainerDashboardResponse.PendingReview(r.clientId(),
                            c != null ? c.getFirstName() : "—", c != null ? c.getLastName() : "",
                            r.planName(), s != null ? s.getRenewalDate() : null, r.nextReviewDate(),
                            Math.max(0, ChronoUnit.DAYS.between(reference, today)));
                })
                .toList();

        // ── Reportes sin feedback ──
        List<WeeklyReport> noFeedbackReports = weeklyReportRepository.findByCoachFeedbackIsNullOrderByCreatedAtDesc();
        List<TrainerDashboardResponse.ReportWithoutFeedback> reportsWithoutFeedback = noFeedbackReports.stream()
                .limit(10)
                .map(r -> new TrainerDashboardResponse.ReportWithoutFeedback(
                        r.getId(), r.getClient().getId(),
                        r.getClient().getFirstName(), r.getClient().getLastName(),
                        r.getCreatedAt(), r.getWeight()))
                .toList();

        // ── Clientes activos sin rutina ──
        Set<Long> withWorkout = clientWorkoutRepository.findAll().stream()
                .filter(ClientWorkout::isActive).map(ClientWorkout::getClientId).collect(Collectors.toSet());
        List<Subscription> withoutWorkoutSubs = activeSubs.stream()
                .filter(s -> !withWorkout.contains(s.getClientId())).toList();
        List<TrainerDashboardResponse.ClientWithoutWorkout> clientsWithoutWorkouts = withoutWorkoutSubs.stream()
                .limit(10)
                .map(s -> {
                    Client c = clients.get(s.getClientId());
                    List<ClientWorkout> history = clientWorkoutRepository.findAllByClientIdOrderByAssignedAtDesc(s.getClientId());
                    return new TrainerDashboardResponse.ClientWithoutWorkout(s.getClientId(),
                            c != null ? c.getFirstName() : "—", c != null ? c.getLastName() : "",
                            s.getPlan().getName(), history.isEmpty() ? null : history.get(0).getWorkout().getName());
                })
                .toList();

        // ── Próximas renovaciones (30 días) ──
        List<TrainerDashboardResponse.UpcomingRenewal> upcomingRenewals = subscriptionRepository
                .findByStatusAndRenewalDateBetweenOrderByRenewalDateAsc(SubscriptionStatus.ACTIVE, today, today.plusDays(30))
                .stream()
                .filter(s -> !s.isCancelAtPeriodEnd())
                .limit(10)
                .map(s -> {
                    Client c = clients.get(s.getClientId());
                    return new TrainerDashboardResponse.UpcomingRenewal(s.getClientId(),
                            c != null ? c.getFirstName() : "—", c != null ? c.getLastName() : "",
                            s.getPlan().getName(), s.effectivePrice(), s.getRenewalDate(),
                            ChronoUnit.DAYS.between(today, s.getRenewalDate()));
                })
                .toList();

        // ── Distribución por plan ──
        List<TrainerDashboardResponse.PlanDistribution> clientsPerPlan = activeSubs.stream()
                .collect(Collectors.groupingBy(s -> s.getPlan().getId()))
                .values().stream()
                .map(subs -> new TrainerDashboardResponse.PlanDistribution(
                        subs.get(0).getPlan().getId(), subs.get(0).getPlan().getName(), subs.size()))
                .sorted((a, b) -> Long.compare(b.clients(), a.clients()))
                .toList();

        // ── Cobros ──
        List<Payment> pending = paymentRepository.findByStatus(PaymentStatus.PENDING);
        long overdue = pending.stream().filter(p -> p.getDueDate().isBefore(today)).count();
        BigDecimal revenueThisMonth = sum(paymentRepository.findByStatusAndPaidAtBetween(PaymentStatus.PAID, monthStart, now));
        BigDecimal mrr = activeSubs.stream().map(Subscription::monthlyEquivalent).reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Actividad reciente ──
        List<TrainerDashboardResponse.ActivityItem> recentActivity = new ArrayList<>();
        clientRepository.findTop5ByOrderByCreatedAtDesc().forEach(c ->
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "CLIENT_CREATED", c.getId(), c.getFirstName(), c.getLastName(),
                        "Nuevo cliente registrado", c.getCreatedAt())));
        weeklyReportRepository.findTop5ByOrderByCreatedAtDesc().forEach(r ->
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "REPORT_SUBMITTED", r.getClient().getId(),
                        r.getClient().getFirstName(), r.getClient().getLastName(),
                        "Seguimiento enviado", r.getCreatedAt())));
        clientWorkoutRepository.findTop10ByOrderByAssignedAtDesc().stream().limit(5).forEach(cw -> {
            Client c = clients.get(cw.getClientId());
            if (c != null) {
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "WORKOUT_ASSIGNED", c.getId(), c.getFirstName(), c.getLastName(),
                        "Rutina asignada: " + cw.getWorkout().getName(), cw.getAssignedAt()));
            }
        });
        workoutLogRepository.findTop10ByOrderByCreatedAtDesc().stream().limit(5).forEach(l -> {
            Client c = clients.get(l.getClientId());
            if (c != null) {
                recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                        "WORKOUT_LOGGED", c.getId(), c.getFirstName(), c.getLastName(),
                        "Entreno registrado: " + l.getDayName(), l.getCreatedAt()));
            }
        });
        paymentRepository.findByStatusAndPaidAtBetween(PaymentStatus.PAID, now.minus(30, ChronoUnit.DAYS), now).stream()
                .sorted(Comparator.comparing(Payment::getPaidAt).reversed())
                .limit(5)
                .forEach(p -> {
                    Client c = clients.get(p.getClientId());
                    if (c != null) {
                        recentActivity.add(new TrainerDashboardResponse.ActivityItem(
                                "PAYMENT_RECEIVED", c.getId(), c.getFirstName(), c.getLastName(),
                                "Pago recibido: " + p.getAmount().setScale(2, RoundingMode.HALF_UP) + " €", p.getPaidAt()));
                    }
                });
        recentActivity.removeIf(a -> a.occurredAt() == null);
        recentActivity.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));

        return new TrainerDashboardResponse(
                totalClients, activeClients, totalReports,
                reviewsPending, reviewsThisWeek, reportsThisWeek,
                noFeedbackReports.size(), newClientsThisMonth,
                withoutWorkoutSubs.size(), upcomingRenewals.size(),
                analysisService.countPendingAnalyses(),
                messageService.countTrainerUnread(),
                pending.size(), sum(pending), overdue,
                taskService.countDueToday(),
                videoService.countPendingReview(),
                revenueThisMonth, mrr,
                clientsPerPlan,
                pendingReviews, reportsWithoutFeedback, clientsWithoutWorkouts, upcomingRenewals,
                recentActivity.stream().limit(15).toList()
        );
    }

    // ── Business overview ───────────────────────────────────────────────────

    public BusinessOverviewResponse getBusinessOverview() {
        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        Instant now = Instant.now();

        List<Subscription> activeSubs = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);
        long paused = subscriptionRepository.countByStatus(SubscriptionStatus.PAUSED);
        long cancellations = subscriptionRepository.countByStatusAndEndDateBetween(
                SubscriptionStatus.CANCELLED, today.minusDays(30), today);
        BigDecimal mrr = activeSubs.stream().map(Subscription::monthlyEquivalent).reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant from12 = thisMonth.minusMonths(11).atDay(1).atStartOfDay(ZONE).toInstant();
        List<Payment> paid12 = paymentRepository.findByStatusAndPaidAtBetween(PaymentStatus.PAID, from12, now);
        List<Payment> refunded12 = paymentRepository.findByStatus(PaymentStatus.REFUNDED).stream()
                .filter(p -> p.getPaidAt() != null && !p.getPaidAt().isBefore(from12)).toList();

        Map<YearMonth, List<Payment>> byMonth = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            byMonth.put(thisMonth.minusMonths(i), new ArrayList<>());
        }
        for (Payment p : paid12) {
            YearMonth ym = YearMonth.from(LocalDate.ofInstant(p.getPaidAt(), ZONE));
            byMonth.computeIfPresent(ym, (k, list) -> {
                list.add(p);
                return list;
            });
        }
        List<MonthAmount> revenueByMonth = byMonth.entrySet().stream()
                .map(e -> new MonthAmount(e.getKey().toString(), sum(e.getValue()), e.getValue().size()))
                .toList();

        Map<PaymentMethod, List<Payment>> byMethod = new EnumMap<>(PaymentMethod.class);
        for (Payment p : paid12) {
            if (p.getMethod() != null) {
                byMethod.computeIfAbsent(p.getMethod(), k -> new ArrayList<>()).add(p);
            }
        }
        List<MethodAmount> revenueByMethod = byMethod.entrySet().stream()
                .map(e -> new MethodAmount(e.getKey().name(), sum(e.getValue()), e.getValue().size()))
                .toList();

        List<PlanBreakdown> plans = activeSubs.stream()
                .collect(Collectors.groupingBy(s -> s.getPlan().getId()))
                .values().stream()
                .map(subs -> {
                    Plan plan = subs.get(0).getPlan();
                    return new PlanBreakdown(plan.getId(), plan.getName(), plan.getMonthlyPrice(), subs.size(),
                            subs.stream().map(Subscription::monthlyEquivalent).reduce(BigDecimal.ZERO, BigDecimal::add));
                })
                .sorted(Comparator.comparing(PlanBreakdown::mrr).reversed())
                .toList();

        Map<YearMonth, Long> newClients = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            newClients.put(thisMonth.minusMonths(i), 0L);
        }
        for (Client c : clientRepository.findAll()) {
            if (c.getCreatedAt() != null) {
                newClients.computeIfPresent(YearMonth.from(LocalDate.ofInstant(c.getCreatedAt(), ZONE)), (k, v) -> v + 1);
            }
        }

        List<Payment> pending = paymentRepository.findByStatus(PaymentStatus.PENDING);
        BigDecimal overdue = sum(pending.stream().filter(p -> p.getDueDate().isBefore(today)).toList());
        long activeCount = activeSubs.size();
        BigDecimal arpc = activeCount == 0 ? BigDecimal.ZERO
                : mrr.divide(BigDecimal.valueOf(activeCount), 2, RoundingMode.HALF_UP);
        long base = activeCount + cancellations;
        BigDecimal churn = base == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(cancellations * 100.0 / base).setScale(1, RoundingMode.HALF_UP);

        return new BusinessOverviewResponse(
                clientRepository.count(), activeCount, paused, cancellations, mrr,
                monthRevenue(byMonth, thisMonth), monthRevenue(byMonth, thisMonth.minusMonths(1)),
                sum(paid12), sum(refunded12), sum(pending), overdue, arpc, churn,
                revenueByMonth, revenueByMethod, plans,
                newClients.entrySet().stream().map(e -> new MonthCount(e.getKey().toString(), e.getValue())).toList());
    }

    // ── Client ──────────────────────────────────────────────────────────────

    public ClientDashboardResponse getClientDashboard() {
        Client client = clientService.getCurrentClient();
        Long clientId = client.getId();
        LocalDate today = LocalDate.now();

        List<WeeklyReportResponse> reports = reportService.findByClientId(clientId);
        WeeklyReportResponse latest = reports.isEmpty() ? null : reports.get(0);
        WeeklyReportResponse first = reports.isEmpty() ? null : reports.get(reports.size() - 1);

        Optional<Subscription> subscription = subscriptionRepository.findByClientIdAndActiveTrue(clientId);
        Optional<Plan> activePlan = subscription.filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE).map(Subscription::getPlan);

        List<Payment> pending = paymentRepository.findByClientIdAndStatus(clientId, PaymentStatus.PENDING);
        LocalDate nextReview = taskService.reviewSchedule().stream()
                .filter(r -> r.clientId().equals(clientId))
                .map(ReviewScheduleItem::nextReviewDate)
                .findFirst().orElse(null);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        return new ClientDashboardResponse(
                clientMapper.toResponse(client),
                reports.size(),
                latest,
                subscription.map(subscriptionMapper::toResponse).orElse(null),
                pending.size(),
                sum(pending),
                pending.stream().anyMatch(p -> p.getDueDate().isBefore(today)),
                nextReview,
                messageService.countMyUnread(),
                activePlan.map(Plan::isMessagingEnabled).orElse(false),
                activePlan.map(Plan::isPdfExportEnabled).orElse(false),
                workoutLogService.countInRange(clientId, weekStart, weekStart.plusDays(6)),
                workoutLogRepository.countByClientId(clientId),
                photoService.countForClient(clientId),
                clientWorkoutRepository.findByClientIdAndActiveTrue(clientId).map(cw -> cw.getWorkout().getName()).orElse(null),
                clientDietRepository.findByClient_IdAndActiveTrue(clientId).map(cd -> cd.getDiet().getName()).orElse(null),
                testimonialRepository.existsByClientId(clientId),
                first != null ? first.weight() : null,
                latest != null ? latest.weight() : null,
                videoService.countUnseenByClient(clientId)
        );
    }

    private static BigDecimal monthRevenue(Map<YearMonth, List<Payment>> byMonth, YearMonth month) {
        return sum(byMonth.getOrDefault(month, List.of()));
    }

    private static BigDecimal sum(List<Payment> payments) {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
