package com.adrifit.backend.common.config;

import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.repository.PlanRepository;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public DataInitializer(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           PlanRepository planRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.planRepository = planRepository;
    }

    @Override
    public void run(String... args) {
        seedTrainer();
        seedPlans();
    }

    private void seedTrainer() {
        if (userRepository.existsByUsername("trainer")) {
            return;
        }
        userRepository.save(User.builder()
                .username("trainer")
                .email("trainer@adrifit.com")
                .password(passwordEncoder.encode("trainer123"))
                .role(Role.TRAINER)
                .build());
        log.info("Default TRAINER user created -> username: 'trainer', password: 'trainer123'");
    }

    private void seedPlans() {
        if (planRepository.count() > 0) {
            return;
        }
        planRepository.save(Plan.builder()
                .name("Basic")
                .description("Entrenamiento y nutrición con seguimiento mensual.")
                .monthlyPrice(new BigDecimal("39.00"))
                .reviewFrequencyDays(30)
                .messagingEnabled(false)
                .analyticsEnabled(false)
                .pdfExportEnabled(false)
                .prioritySupport(false)
                .active(true)
                .build());
        planRepository.save(Plan.builder()
                .name("Advanced")
                .description("Seguimiento cada 15 días con mensajería y analíticas.")
                .monthlyPrice(new BigDecimal("69.00"))
                .reviewFrequencyDays(15)
                .messagingEnabled(true)
                .analyticsEnabled(true)
                .pdfExportEnabled(false)
                .prioritySupport(false)
                .active(true)
                .build());
        planRepository.save(Plan.builder()
                .name("Premium")
                .description("Revisión semanal, soporte prioritario y exportación PDF.")
                .monthlyPrice(new BigDecimal("119.00"))
                .reviewFrequencyDays(7)
                .messagingEnabled(true)
                .analyticsEnabled(true)
                .pdfExportEnabled(true)
                .prioritySupport(true)
                .active(true)
                .build());
        log.info("Seeded default plans: Basic, Advanced, Premium");
    }
}
