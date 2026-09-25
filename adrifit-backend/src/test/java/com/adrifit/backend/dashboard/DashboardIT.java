package com.adrifit.backend.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DashboardIT extends AbstractIntegrationTest {

    @Test
    void trainerBusinessAndClientDashboards() {
        String trainer = trainerToken();
        Long clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345");
        String client = login("ana", "ana12345");
        Long paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE client_id = ?", Long.class, clientId);
        post("/api/payments/me/" + paymentId + "/card", Map.of("cardNumber", "4242424242424242", "expMonth", 1,
                "expYear", LocalDate.now().getYear() + 1, "cvc", "123", "holderName", "Ana"), client);
        post("/api/messages/me", Map.of("content", "hola"), client);

        ResponseEntity<Map<String, Object>> trainerDash = get("/api/dashboard/trainer", trainer);
        assertThat(trainerDash.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(trainerDash.getBody().get("totalClients")).isEqualTo(1);
        assertThat(trainerDash.getBody().get("unreadMessages")).isEqualTo(1);
        assertThat(((Number) trainerDash.getBody().get("revenueThisMonth")).doubleValue()).isEqualTo(119.0);
        assertThat(((Number) trainerDash.getBody().get("monthlyRecurringRevenue")).doubleValue()).isEqualTo(119.0);

        ResponseEntity<Map<String, Object>> business = get("/api/dashboard/business", trainer);
        assertThat(business.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<?> byMonth = (List<?>) business.getBody().get("revenueByMonth");
        assertThat(byMonth).hasSize(12);
        assertThat(((Number) business.getBody().get("revenueThisMonth")).doubleValue()).isEqualTo(119.0);
        assertThat((List<?>) business.getBody().get("revenueByMethod")).hasSize(1);

        ResponseEntity<Map<String, Object>> clientDash = get("/api/dashboard/client", client);
        assertThat(clientDash.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clientDash.getBody().get("pendingPayments")).isEqualTo(0);
        assertThat(clientDash.getBody().get("messagingEnabled")).isEqualTo(true);
        assertThat(clientDash.getBody().get("nextReviewDate")).isEqualTo(LocalDate.now().plusDays(7).toString());

        assertThat(get("/api/dashboard/business", client).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void workoutPdf_clientOnlyOwnAndOnlyWithPlanFeature() {
        String trainer = trainerToken();
        Long anaId = createClient(trainer, "ana", "ana@mail.com", "ana12345", "Premium");
        Long bobId = createClient(trainer, "bob", "bob@mail.com", "bob12345", "Basic");
        Long workoutId = id(post("/api/workouts", Map.of("name", "Full body", "exercises",
                List.of(Map.of("exerciseName", "Sentadilla", "sets", 3, "reps", 8, "orderIndex", 0))), trainer));
        post("/api/workouts/clients/" + anaId + "/assign", Map.of("workoutId", workoutId), trainer);
        post("/api/workouts/clients/" + bobId + "/assign", Map.of("workoutId", workoutId), trainer);
        String ana = login("ana", "ana12345");
        String bob = login("bob", "bob12345");

        assertThat(rest.exchange("/api/workouts/" + workoutId + "/pdf/" + anaId, org.springframework.http.HttpMethod.GET,
                auth(ana), byte[].class).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Someone else's PDF
        assertThat(rest.exchange("/api/workouts/" + workoutId + "/pdf/" + anaId, org.springframework.http.HttpMethod.GET,
                auth(bob), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Basic plan has no PDF export
        assertThat(rest.exchange("/api/workouts/" + workoutId + "/pdf/" + bobId, org.springframework.http.HttpMethod.GET,
                auth(bob), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void emailsOutbox_andCustomEmail() {
        String trainer = trainerToken();
        Long clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345");

        ResponseEntity<List<Map<String, Object>>> sent = rest.exchange("/api/emails", org.springframework.http.HttpMethod.POST,
                entity(Map.of("clientIds", List.of(clientId), "subject", "Novedades", "body", "Hola <b>equipo</b>"), trainer), LIST);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) sent.getBody().get(0).get("htmlBody")).contains("Hola &lt;b&gt;equipo&lt;/b&gt;");

        List<Map<String, Object>> outbox = getList("/api/emails?clientId=" + clientId, trainer).getBody();
        assertThat(outbox).extracting(m -> m.get("type")).contains("WELCOME", "PAYMENT_DUE", "CUSTOM");
        assertThat(outbox).allMatch(m -> m.get("status").equals("TEST_CAPTURED"));
    }
}
