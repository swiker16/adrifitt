package com.adrifit.backend.report.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateWeeklyReportRequest(
        @NotNull(message = "Weight is required")
        @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg")
        @DecimalMax(value = "400.0", message = "Weight must be at most 400 kg")
        BigDecimal weight,

        @DecimalMin(value = "20.0", message = "Waist must be at least 20 cm")
        @DecimalMax(value = "300.0", message = "Waist must be at most 300 cm")
        BigDecimal waist,

        @DecimalMin(value = "1.0", message = "Body fat must be at least 1%")
        @DecimalMax(value = "70.0", message = "Body fat must be at most 70%")
        BigDecimal bodyFat,

        @Min(value = 1, message = "Energy level must be between 1 and 10")
        @Max(value = 10, message = "Energy level must be between 1 and 10")
        Integer energyLevel,

        @Min(value = 0, message = "Diet adherence must be between 0 and 100")
        @Max(value = 100, message = "Diet adherence must be between 0 and 100")
        Integer dietAdherence,

        @Min(value = 0, message = "Training adherence must be between 0 and 100")
        @Max(value = 100, message = "Training adherence must be between 0 and 100")
        Integer trainingAdherence,

        @Size(max = 1000, message = "Comments must be at most 1000 characters")
        String comments
) {
}
