package com.adrifit.backend.lead.dto;

import com.adrifit.backend.plan.domain.BillingPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Initial questionnaire filled in by a prospective client before the trainer accepts them.
 * Stored as JSON on the lead; shown to the trainer in the request and later in the client file.
 */
public record Questionnaire(
        // ── Sobre ti ──
        @NotNull(message = "Indica tu fecha de nacimiento") @Past(message = "La fecha de nacimiento no es válida")
        LocalDate birthDate,
        @NotBlank(message = "Indica tu sexo") @Pattern(regexp = "MUJER|HOMBRE|OTRO")
        String sex,
        @NotNull(message = "Indica tu altura") @DecimalMin(value = "100", message = "Altura en cm (100-250)") @DecimalMax(value = "250", message = "Altura en cm (100-250)")
        BigDecimal heightCm,
        @NotNull(message = "Indica tu peso") @DecimalMin(value = "30", message = "Peso en kg (30-300)") @DecimalMax(value = "300", message = "Peso en kg (30-300)")
        BigDecimal weightKg,
        @Size(max = 100)
        String occupation,
        @NotBlank(message = "Indica tu nivel de actividad diaria") @Pattern(regexp = "SEDENTARIO|LIGERO|ACTIVO|MUY_ACTIVO")
        String activityLevel,

        // ── Objetivo y experiencia ──
        @NotBlank(message = "Elige tu objetivo principal") @Pattern(regexp = "PERDER_GRASA|GANAR_MUSCULO|RECOMPOSICION|RENDIMIENTO|SALUD")
        String mainGoal,
        @Size(max = 1000)
        String goalDetails,
        @NotBlank(message = "Indica tu experiencia entrenando") @Pattern(regexp = "NINGUNA|MENOS_1|ENTRE_1_3|MAS_3")
        String experience,
        @Size(max = 500)
        String currentTraining,

        // ── Salud ──
        boolean hasInjuries,
        @Size(max = 1000)
        String injuries,
        boolean hasMedicalConditions,
        @Size(max = 1000)
        String medicalConditions,
        @Size(max = 500)
        String medication,
        @Size(max = 500)
        String surgeries,
        Boolean recentBloodTest,

        // ── Entrenamiento ──
        @NotNull(message = "Indica cuántos días puedes entrenar") @Min(1) @Max(7)
        Integer daysPerWeek,
        @NotNull(message = "Indica cuánto tiempo tienes por sesión") @Min(15) @Max(240)
        Integer minutesPerSession,
        @NotBlank(message = "Indica dónde entrenarás") @Pattern(regexp = "GIMNASIO|CASA|EXTERIOR|MIXTO")
        String trainingPlace,
        @Size(max = 500)
        String equipment,

        // ── Alimentación y hábitos ──
        @NotBlank(message = "Indica tu tipo de alimentación") @Pattern(regexp = "OMNIVORA|VEGETARIANA|VEGANA|OTRA")
        String dietType,
        @Size(max = 500)
        String allergies,
        @Min(1) @Max(10)
        Integer mealsPerDay,
        @Size(max = 500)
        String dislikedFoods,
        @DecimalMin("3") @DecimalMax("14")
        BigDecimal sleepHours,
        @Min(1) @Max(5)
        Integer stressLevel,
        @Size(max = 300)
        String alcoholTobacco,

        // ── Plan ──
        Long planId,
        BillingPeriod billingPeriod,
        @Size(max = 100)
        String howFound,
        @Size(max = 1500)
        String comments,

        /** Explicit consent to process health data (GDPR art. 9). */
        @AssertTrue(message = "Necesitamos tu consentimiento para tratar tus datos de salud")
        boolean healthConsent
) {
}
