package com.adrifit.backend.common.config;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.diet.domain.ClientDiet;
import com.adrifit.backend.diet.domain.Diet;
import com.adrifit.backend.diet.domain.DietDay;
import com.adrifit.backend.diet.domain.DietFood;
import com.adrifit.backend.diet.domain.DietMeal;
import com.adrifit.backend.diet.repository.ClientDietRepository;
import com.adrifit.backend.diet.repository.DietRepository;
import com.adrifit.backend.message.domain.Message;
import com.adrifit.backend.message.repository.MessageRepository;
import com.adrifit.backend.payment.domain.Payment;
import com.adrifit.backend.payment.domain.PaymentMethod;
import com.adrifit.backend.payment.domain.PaymentStatus;
import com.adrifit.backend.payment.repository.PaymentRepository;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.repository.PlanRepository;
import com.adrifit.backend.report.domain.ReportStatus;
import com.adrifit.backend.report.domain.WeeklyReport;
import com.adrifit.backend.report.repository.WeeklyReportRepository;
import com.adrifit.backend.subscription.domain.Subscription;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import com.adrifit.backend.testimonial.domain.Testimonial;
import com.adrifit.backend.testimonial.repository.TestimonialRepository;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import com.adrifit.backend.workout.domain.ClientWorkout;
import com.adrifit.backend.workout.domain.Workout;
import com.adrifit.backend.workout.domain.WorkoutExercise;
import com.adrifit.backend.workout.repository.ClientWorkoutRepository;
import com.adrifit.backend.workout.repository.WorkoutRepository;
import com.adrifit.backend.workoutlog.domain.WorkoutLog;
import com.adrifit.backend.workoutlog.domain.WorkoutLogSet;
import com.adrifit.backend.workoutlog.repository.WorkoutLogRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional demo content (adrifit.seed.demo-data=true, on by default in the "local" profile) so the
 * app can be explored right away:
 * <ul>
 *   <li>cliente / cliente123 — Laura Martín, plan Premium with special price (85 €/month), with routine, diet, check-ins, logs, chat.</li>
 *   <li>carlos / carlos123 — Carlos Ruiz, plan Básica, with an overdue payment and no routine.</li>
 * </ul>
 */
@Component
@Order(2)
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final boolean enabled;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final WorkoutRepository workoutRepository;
    private final ClientWorkoutRepository clientWorkoutRepository;
    private final DietRepository dietRepository;
    private final ClientDietRepository clientDietRepository;
    private final WeeklyReportRepository reportRepository;
    private final WorkoutLogRepository workoutLogRepository;
    private final MessageRepository messageRepository;
    private final TestimonialRepository testimonialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(@Value("${adrifit.seed.demo-data:false}") boolean enabled,
                          UserRepository userRepository,
                          ClientRepository clientRepository,
                          PlanRepository planRepository,
                          SubscriptionRepository subscriptionRepository,
                          PaymentRepository paymentRepository,
                          WorkoutRepository workoutRepository,
                          ClientWorkoutRepository clientWorkoutRepository,
                          DietRepository dietRepository,
                          ClientDietRepository clientDietRepository,
                          WeeklyReportRepository reportRepository,
                          WorkoutLogRepository workoutLogRepository,
                          MessageRepository messageRepository,
                          TestimonialRepository testimonialRepository,
                          PasswordEncoder passwordEncoder,
                          JdbcTemplate jdbc) {
        this.enabled = enabled;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.workoutRepository = workoutRepository;
        this.clientWorkoutRepository = clientWorkoutRepository;
        this.dietRepository = dietRepository;
        this.clientDietRepository = clientDietRepository;
        this.reportRepository = reportRepository;
        this.workoutLogRepository = workoutLogRepository;
        this.messageRepository = messageRepository;
        this.testimonialRepository = testimonialRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || userRepository.existsByUsername("cliente")) {
            return;
        }
        User trainer = userRepository.findByUsername("trainer").orElse(null);
        List<Plan> plans = planRepository.findAllByOrderByMonthlyPriceAsc();
        if (trainer == null || plans.size() < 2) {
            return;
        }
        Plan basic = plans.get(0);
        Plan premium = plans.get(plans.size() - 1);
        LocalDate today = LocalDate.now();

        // ── Laura (Premium) ──
        Client laura = createClient("cliente", "cliente123", "laura@demo.adrifitt.app", "Laura", "Martín",
                "612345678", LocalDate.of(1994, 5, 12), "Perder grasa y ganar fuerza", trainer.getId());
        LocalDate lauraStart = today.minusDays(20);
        Subscription lauraSub = subscriptionRepository.save(Subscription.builder()
                .clientId(laura.getId()).plan(premium).startDate(lauraStart).renewalDate(lauraStart.plusMonths(1))
                .customPrice(new BigDecimal("85.00")).customPriceNote("Condiciones especiales")
                .status(SubscriptionStatus.ACTIVE).active(true).build());
        paymentRepository.save(Payment.builder()
                .clientId(laura.getId()).subscriptionId(lauraSub.getId())
                .concept("Plan " + premium.getName() + " · primer mes").amount(lauraSub.effectivePrice()).currency("EUR")
                .status(PaymentStatus.PAID).method(PaymentMethod.CARD).cardBrand("VISA").cardLast4("4242")
                .providerReference("test_card_demo0001").dueDate(lauraStart).periodStart(lauraStart)
                .periodEnd(lauraStart.plusMonths(1)).paidAt(lauraStart.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(3600))
                .build());

        Workout workout = createWorkout();
        clientWorkoutRepository.save(ClientWorkout.builder().clientId(laura.getId()).workout(workout).active(true).build());
        Diet diet = createDiet(trainer.getId());
        clientDietRepository.save(ClientDiet.builder().client(laura).diet(diet).assignedAt(Instant.now())
                .startDate(lauraStart).active(true).trainerNotes("Bebe al menos 2,5 L de agua al día.").build());

        double[] weights = {68.4, 67.9, 67.3, 66.8};
        double[] waists = {76.0, 75.2, 74.5, 73.9};
        for (int i = 0; i < weights.length; i++) {
            boolean reviewed = i < weights.length - 1;
            WeeklyReport report = reportRepository.save(WeeklyReport.builder()
                    .client(laura).weight(BigDecimal.valueOf(weights[i])).waist(BigDecimal.valueOf(waists[i]))
                    .bodyFat(BigDecimal.valueOf(26.5 - i * 0.6)).energyLevel(7 + (i % 2)).dietAdherence(85 + i * 3)
                    .trainingAdherence(90).comments(i == weights.length - 1 ? "Semana muy buena, más energía en los entrenos." : "Bien en general.")
                    .coachFeedback(reviewed ? "¡Buen progreso! Mantén la proteína alta y sube carga en básicos." : null)
                    .status(reviewed ? ReportStatus.REVIEWED : ReportStatus.PENDING)
                    .reviewedAt(reviewed ? Instant.now().minus((long) (weights.length - i) * 7 - 1, ChronoUnit.DAYS) : null)
                    .build());
            backdate("weekly_reports", report.getId(), Instant.now().minus((long) (weights.length - 1 - i) * 7, ChronoUnit.DAYS));
        }

        List<WorkoutExercise> day1 = workout.getExercises().stream().filter(e -> e.getDayNumber() == 1).toList();
        for (int week = 2; week >= 1; week--) {
            WorkoutLog wl = WorkoutLog.builder().clientId(laura.getId()).workoutId(workout.getId()).workoutName(workout.getName())
                    .dayNumber(1).dayName(day1.get(0).getDayName()).performedOn(today.minusDays(week * 7L - 1))
                    .notes(week == 1 ? "Me he sentido fuerte" : null).build();
            for (int e = 0; e < day1.size(); e++) {
                WorkoutExercise ex = day1.get(e);
                for (int s = 1; s <= ex.getSets(); s++) {
                    wl.getSets().add(WorkoutLogSet.builder().log(wl).exerciseId(ex.getId()).exerciseName(ex.getExerciseName())
                            .exerciseOrder(e).setNumber(s)
                            .weightKg(BigDecimal.valueOf(20 + e * 10 + (2 - week) * 2.5))
                            .reps(ex.getReps()).rir(2).build());
                }
            }
            workoutLogRepository.save(wl);
        }

        messageRepository.save(Message.builder().clientId(laura.getId()).senderUserId(trainer.getId()).senderRole(Role.TRAINER)
                .content("¡Hola Laura! Ya tienes tu rutina y tu dieta. Cualquier duda me escribes por aquí.").readAt(Instant.now()).build());
        messageRepository.save(Message.builder().clientId(laura.getId()).senderUserId(laura.getUserId()).senderRole(Role.CLIENT)
                .content("¡Gracias! ¿Puedo cambiar el arroz de la comida por patata?").build());

        // ── Carlos (Básica) ──
        Client carlos = createClient("carlos", "carlos123", "carlos@demo.adrifitt.app", "Carlos", "Ruiz",
                "698765432", LocalDate.of(1988, 11, 3), "Mejorar la salud y perder peso", trainer.getId());
        LocalDate carlosStart = today.minusDays(35);
        Subscription carlosSub = subscriptionRepository.save(Subscription.builder()
                .clientId(carlos.getId()).plan(basic).startDate(carlosStart).renewalDate(carlosStart.plusMonths(2))
                .status(SubscriptionStatus.ACTIVE).active(true).build());
        paymentRepository.save(Payment.builder()
                .clientId(carlos.getId()).subscriptionId(carlosSub.getId())
                .concept("Plan " + basic.getName() + " · primer mes").amount(basic.getMonthlyPrice()).currency("EUR")
                .status(PaymentStatus.PAID).method(PaymentMethod.CASH).dueDate(carlosStart).periodStart(carlosStart)
                .periodEnd(carlosStart.plusMonths(1)).paidAt(carlosStart.atStartOfDay(ZoneId.systemDefault()).toInstant())
                .build());
        paymentRepository.save(Payment.builder()
                .clientId(carlos.getId()).subscriptionId(carlosSub.getId())
                .concept("Plan " + basic.getName() + " · segundo mes").amount(basic.getMonthlyPrice()).currency("EUR")
                .status(PaymentStatus.PENDING).dueDate(carlosStart.plusMonths(1)).periodStart(carlosStart.plusMonths(1))
                .periodEnd(carlosStart.plusMonths(2)).build());
        backdate("clients", carlos.getId(), Instant.now().minus(35, ChronoUnit.DAYS));
        testimonialRepository.save(Testimonial.builder().clientId(carlos.getId()).authorName("Carlos R.")
                .planName(basic.getName()).rating(5)
                .content("En un mes he cambiado mis hábitos por completo. El seguimiento es cercano y muy profesional.")
                .visible(true).build());

        log.info("Demo data created -> cliente/cliente123 (Premium), carlos/carlos123 (Básica)");
    }

    private Client createClient(String username, String password, String email, String firstName, String lastName,
                                String phone, LocalDate birthDate, String objective, Long trainerId) {
        User user = userRepository.save(User.builder().username(username).email(email)
                .password(passwordEncoder.encode(password)).role(Role.CLIENT).mustChangePassword(false).build());
        return clientRepository.save(Client.builder().userId(user.getId()).firstName(firstName).lastName(lastName)
                .phone(phone).birthDate(birthDate).objective(objective).trainerId(trainerId).build());
    }

    private Workout createWorkout() {
        Workout w = Workout.builder().name("Torso / Pierna · 4 días").objective("Recomposición corporal")
                .description("Rutina de fuerza con progresión de cargas. Descansa 2-3 min en básicos.")
                .daysPerWeek(4).build();
        Object[][] exercises = {
                {1, "Torso A", "Press banca", 4, 8, 2}, {1, "Torso A", "Remo con barra", 4, 10, 2},
                {1, "Torso A", "Press militar mancuernas", 3, 10, 2}, {1, "Torso A", "Jalón al pecho", 3, 12, 1},
                {2, "Pierna A", "Sentadilla", 4, 8, 2}, {2, "Pierna A", "Peso muerto rumano", 3, 10, 2},
                {2, "Pierna A", "Prensa", 3, 12, 1}, {2, "Pierna A", "Curl femoral", 3, 12, 1},
                {3, "Torso B", "Press inclinado mancuernas", 4, 10, 2}, {3, "Torso B", "Dominadas asistidas", 4, 8, 2},
                {3, "Torso B", "Elevaciones laterales", 3, 15, 1},
                {4, "Pierna B", "Hip thrust", 4, 10, 2}, {4, "Pierna B", "Zancadas", 3, 12, 2},
                {4, "Pierna B", "Extensión de cuádriceps", 3, 15, 1},
        };
        int order = 0;
        for (Object[] e : exercises) {
            w.getExercises().add(WorkoutExercise.builder().workout(w).dayNumber((Integer) e[0]).dayName((String) e[1])
                    .exerciseName((String) e[2]).sets((Integer) e[3]).reps((Integer) e[4]).rir((Integer) e[5])
                    .restSeconds(120).orderIndex(order++).build());
        }
        return workoutRepository.save(w);
    }

    private Diet createDiet(Long trainerId) {
        Diet diet = Diet.builder().name("Definición 1.900 kcal").objective("Pérdida de grasa")
                .description("Dieta alta en proteína con 4 comidas.").active(true).trainerId(trainerId).build();
        DietDay day = DietDay.builder().diet(diet).name("Día tipo").dayNumber(1).orderIndex(0).build();
        diet.getDays().add(day);
        Object[][] meals = {
                {"Desayuno", "08:00", new Object[][]{{"Copos de avena", 60, 225, 8, 38, 4}, {"Claras de huevo", 200, 104, 22, 1, 0}}},
                {"Comida", "14:00", new Object[][]{{"Arroz basmati (crudo)", 80, 280, 6, 62, 1}, {"Pechuga de pollo", 150, 165, 34, 0, 3}}},
                {"Merienda", "18:00", new Object[][]{{"Yogur griego 0%", 200, 118, 20, 8, 0}, {"Nueces", 20, 131, 3, 3, 13}}},
                {"Cena", "21:30", new Object[][]{{"Salmón", 150, 312, 30, 0, 20}, {"Verduras al horno", 250, 90, 4, 15, 1}}},
        };
        int mi = 0;
        for (Object[] m : meals) {
            DietMeal meal = DietMeal.builder().dietDay(day).name((String) m[0]).time((String) m[1]).orderIndex(mi++).build();
            int fi = 0;
            for (Object[] f : (Object[][]) m[2]) {
                meal.getFoods().add(DietFood.builder().dietMeal(meal).foodName((String) f[0])
                        .quantity(BigDecimal.valueOf((Integer) f[1])).unit("g")
                        .calories(BigDecimal.valueOf((Integer) f[2])).proteinGrams(BigDecimal.valueOf((Integer) f[3]))
                        .carbsGrams(BigDecimal.valueOf((Integer) f[4])).fatGrams(BigDecimal.valueOf((Integer) f[5]))
                        .orderIndex(fi++).build());
            }
            day.getMeals().add(meal);
        }
        return dietRepository.save(diet);
    }

    /** created_at is filled by JPA auditing; demo history needs past dates. */
    private void backdate(String table, Long id, Instant createdAt) {
        jdbc.update("UPDATE " + table + " SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), id);
    }
}
