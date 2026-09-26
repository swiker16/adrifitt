package com.adrifit.backend.testimonial.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TestimonialDtos {

    private TestimonialDtos() {
    }

    public record CreateTestimonialRequest(
            @NotNull(message = "La valoración es obligatoria") @Min(1) @Max(5) Integer rating,
            @NotBlank(message = "Escribe tu reseña")
            @Size(min = 10, max = 1000, message = "La reseña debe tener entre 10 y 1000 caracteres") String content
    ) {
    }

    /** Public view (landing): no client id. */
    public record PublicTestimonial(Long id, String authorName, String planName, int rating, String content, Instant createdAt) {
    }

    public record TestimonialResponse(
            Long id,
            Long clientId,
            String authorName,
            String planName,
            int rating,
            String content,
            boolean visible,
            Instant createdAt
    ) {
    }

    public record VisibilityRequest(@NotNull Boolean visible) {
    }
}
