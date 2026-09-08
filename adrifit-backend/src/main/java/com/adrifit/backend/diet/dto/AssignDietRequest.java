package com.adrifit.backend.diet.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AssignDietRequest(
        @NotNull Long dietId,
        LocalDate startDate,
        @Size(max = 1000) String trainerNotes
) {}
