package com.adrifit.backend.common.config;

import com.adrifit.backend.common.security.RandomPasswords;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.repository.PlanRepository;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlanRepository planRepository;
    private final String trainerUsername;
    private final String trainerEmail;
    private final String trainerPassword;

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           PlanRepository planRepository,
                           @Value("${adrifit.bootstrap.trainer-username:trainer}") String trainerUsername,
                           @Value("${adrifit.bootstrap.trainer-email:trainer@adrifit.com}") String trainerEmail,
                           @Value("${adrifit.bootstrap.trainer-password:}") String trainerPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.planRepository = planRepository;
        this.trainerUsername = trainerUsername;
        this.trainerEmail = trainerEmail;
        this.trainerPassword = trainerPassword;
    }

    @Override
    public void run(String... args) {
        seedTrainer();
        seedPlans();
    }

    /**
     * First trainer account. The password comes from TRAINER_PASSWORD; without it a random one is
     * generated and shown once in the log (never a known default).
     */
    private void seedTrainer() {
        if (userRepository.existsByUsername(trainerUsername) || !userRepository.findByRole(Role.TRAINER).isEmpty()) {
            return;
        }
        boolean configured = trainerPassword != null && !trainerPassword.isBlank();
        String password = configured ? trainerPassword : RandomPasswords.generate(16);
        userRepository.save(User.builder()
                .username(trainerUsername)
                .email(trainerEmail)
                .password(passwordEncoder.encode(password))
                .role(Role.TRAINER)
                .build());
        if (configured) {
            log.info("Trainer account '{}' created with the password from TRAINER_PASSWORD", trainerUsername);
        } else {
            log.warn("Trainer account '{}' created with a RANDOM password (shown only now): {}  "
                    + "-> sign in and change it in Ajustes, or set TRAINER_PASSWORD before the first start.",
                    trainerUsername, password);
        }
    }

    /** Plan features are stored one per line. */
    private static final String LINE = "\n";

    /** Default catalogue for a fresh database (same content as migration V4). */
    private void seedPlans() {
        if (planRepository.count() > 0) {
            return;
        }
        planRepository.save(Plan.builder()
                .name("Básica")
                .description("Un servicio diseñado para optimizar tu físico, rendimiento y salud mediante un enfoque totalmente personalizado.")
                .monthlyPrice(new BigDecimal("117.00"))
                .quarterlyPrice(new BigDecimal("345.00"))
                .semiannualPrice(new BigDecimal("667.00"))
                .annualPrice(new BigDecimal("1295.00"))
                .reviewFrequencyDays(15)
                .messagingEnabled(true)
                .analyticsEnabled(true)
                .pdfExportEnabled(true)
                .prioritySupport(false)
                .active(true)
                .features(String.join(LINE,
                        "Planificación nutricional: diseño dietético individualizado adaptado a tus objetivos y estilo de vida",
                        "Programa de entrenamiento: planificación estructurada con progresión de cargas (exclusivo para personas asintomáticas o sin patologías del aparato locomotor activas: óseas, tendinosas o articulares)",
                        "Monitoreo en Google Drive: registro sistémico de medidas corporales, cargas y rendimiento",
                        "Reajuste estratégico: feedback continuo y modificaciones técnicas según tu evolución",
                        "Guía de suplementación: protocolo personalizado según tus necesidades particulares",
                        "Control analítico: una (1) analítica sanguínea con interpretación profesional y recomendaciones",
                        "Atención al cliente: soporte directo vía WhatsApp de lunes a viernes de 06:00 a 16:00 h"))
                .build());
        planRepository.save(Plan.builder()
                .name("Premium")
                .description("Todo lo de la tarifa Básica más optimización fisiológica, control clínico avanzado, seguimiento semanal y soporte exclusivo.")
                .monthlyPrice(new BigDecimal("143.00"))
                .quarterlyPrice(new BigDecimal("429.00"))
                .semiannualPrice(new BigDecimal("843.00"))
                .annualPrice(new BigDecimal("1573.00"))
                .reviewFrequencyDays(7)
                .messagingEnabled(true)
                .analyticsEnabled(true)
                .pdfExportEnabled(true)
                .prioritySupport(true)
                .active(true)
                .features(String.join(LINE,
                        "Todo lo incluido en la tarifa Básica",
                        "Monitoreo endocrino-metabólico y fertilidad: control y optimización del entorno hormonal, perfiles tiroideos, ejes hormonales y marcadores de fertilidad",
                        "Interpretación sistémica de analíticas complejas: análisis periódico y avanzado de cribados sanguíneos (perfil lipídico, hepático, renal y hormonal) con recomendaciones específicas",
                        "Gestión de farmacocinética y profilaxis: supervisión de la interacción de medicamentos, asimilación de sustancias y control ante patologías previas",
                        "Coordinación de diagnóstico por imagen: gestión y análisis de pruebas de imagen médica (ecografías o resonancias) para el control de lesiones o composición corporal interna",
                        "Auditoría fisiológica semanal: reporte semanal en lugar de cada 15 días, con ajustes estratégicos de alta frecuencia",
                        "Ecosistema de análisis predictivo: plantillas y software profesional para predecir picos de rendimiento y estancamientos",
                        "Horario de atención extendido con soporte prioritario: lunes a viernes de 07:00 a 17:00 h y sábados de 07:00 a 14:00 h"))
                .build());
        log.info("Seeded default plans: Básica, Premium");
    }
}
