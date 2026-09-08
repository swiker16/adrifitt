package com.adrifit.backend.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoachFeedbackRequest(
        @NotBlank(message = "Coach feedback is required")
        @Size(max = 1000, message = "Coach feedback must be at most 1000 characters")
        String coachFeedback
) {
}
