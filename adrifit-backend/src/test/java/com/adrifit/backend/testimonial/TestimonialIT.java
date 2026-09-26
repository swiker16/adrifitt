package com.adrifit.backend.testimonial;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TestimonialIT extends AbstractIntegrationTest {

    @Test
    void clientPublishesOnce_andItAppearsOnPublicLanding() {
        String trainer = trainerToken();
        createClient(trainer, "ana", "ana@mail.com", "ana12345");
        String client = login("ana", "ana12345");

        assertThat(get("/api/testimonials/me", client).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> created = post("/api/testimonials/me",
                Map.of("rating", 5, "content", "El mejor entrenador que he tenido"), client);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("authorName")).isEqualTo("Test U.");
        assertThat(created.getBody().get("planName")).isEqualTo("Premium");

        ResponseEntity<Map<String, Object>> second = post("/api/testimonials/me",
                Map.of("rating", 4, "content", "Intento publicar otra reseña"), client);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Public endpoint, no token.
        ResponseEntity<List<Map<String, Object>>> landing = getList("/api/public/testimonials", null);
        assertThat(landing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(landing.getBody()).hasSize(1);
        assertThat(landing.getBody().get(0)).doesNotContainKey("clientId");

        // Trainer cannot write reviews, but can hide one.
        assertThat(post("/api/testimonials/me", Map.of("rating", 5, "content", "Soy el entrenador"), trainer).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        Long id = ((Number) created.getBody().get("id")).longValue();
        assertThat(patch("/api/testimonials/" + id + "/visibility", Map.of("visible", false), trainer).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(getList("/api/public/testimonials", null).getBody()).isEmpty();
    }

    @Test
    void invalidRating_isRejected() {
        createClient(trainerToken(), "ana", "ana@mail.com", "ana12345");
        assertThat(post("/api/testimonials/me", Map.of("rating", 6, "content", "Rating fuera de rango"),
                login("ana", "ana12345")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
