package com.adrifit.backend.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record UploadAnalysisRequest(
        @NotBlank String title,
        @NotNull LocalDate analysisDate,
        String clientComment
) {
}
