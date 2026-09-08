package com.adrifit.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class WeeklyReportIT extends AbstractIntegrationTest {

    private String trainer;
    private String client;
    private Long clientId;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
        clientId = createClient(trainer, "juan", "juan@mail.com", "juan12345");
        client = login("juan", "juan12345");
    }

    private Map<String, Object> sampleReport() {
        return Map.of(
                "weight", 80.2,
                "waist", 85.0,
                "bodyFat", 18.5,
                "energyLevel", 7,
                "dietAdherence", 90,
                "trainingAdherence", 80,
                "comments", "Semana dura");
    }

    @Test
    void clientCreatesReport_isPersistedWithCorrectData() {
        ResponseEntity<Map> response = rest.exchange(
                "/api/clients/" + clientId + "/reports", HttpMethod.POST,
                entity(sampleReport(), client), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_reports WHERE client_id = ?", Long.class, clientId);
        assertThat(count).isEqualTo(1L);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT weight, waist, body_fat, energy_level, diet_adherence, training_adherence, comments, "
                        + "coach_feedback, created_at FROM weekly_reports WHERE client_id = ?", clientId);

        assertThat(((BigDecimal) row.get("weight"))).isEqualByComparingTo("80.2");
        assertThat(((BigDecimal) row.get("waist"))).isEqualByComparingTo("85.0");
        assertThat(((BigDecimal) row.get("body_fat"))).isEqualByComparingTo("18.5");
        assertThat(((Number) row.get("energy_level")).intValue()).isEqualTo(7);
        assertThat(((Number) row.get("diet_adherence")).intValue()).isEqualTo(90);
        assertThat(((Number) row.get("training_adherence")).intValue()).isEqualTo(80);
        assertThat(row.get("comments")).isEqualTo("Semana dura");
        assertThat(row.get("coach_feedback")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void trainerListsClientReports() {
        rest.exchange("/api/clients/" + clientId + "/reports", HttpMethod.POST,
                entity(sampleReport(), client), Map.class);

        ResponseEntity<List> response = rest.exchange(
                "/api/clients/" + clientId + "/reports", HttpMethod.GET, auth(trainer), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void clientUpdatesOwnReport_isPersisted() {
        Long reportId = createReportAndGetId();

        Map<String, Object> update = Map.of(
                "weight", 79.0,
                "waist", 83.0,
                "bodyFat", 17.0,
                "energyLevel", 9,
                "dietAdherence", 100,
                "trainingAdherence", 95,
                "comments", "Mejor semana");

        ResponseEntity<Map> response = rest.exchange(
                "/api/reports/" + reportId, HttpMethod.PUT, entity(update, client), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        BigDecimal weight = jdbc.queryForObject(
                "SELECT weight FROM weekly_reports WHERE id = ?", BigDecimal.class, reportId);
        assertThat(weight).isEqualByComparingTo("79.0");
    }

    @Test
    void trainerSetsCoachFeedback_isPersisted() {
        Long reportId = createReportAndGetId();

        Map<String, String> feedback = Map.of("coachFeedback", "Buen trabajo, sube proteina");

        ResponseEntity<Map> response = rest.exchange(
                "/api/reports/" + reportId + "/feedback", HttpMethod.PATCH,
                entity(feedback, trainer), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String stored = jdbc.queryForObject(
                "SELECT coach_feedback FROM weekly_reports WHERE id = ?", String.class, reportId);
        assertThat(stored).isEqualTo("Buen trabajo, sube proteina");
    }

    @Test
    void deleteReport_removesRow() {
        Long reportId = createReportAndGetId();

        ResponseEntity<Void> response = rest.exchange(
                "/api/reports/" + reportId, HttpMethod.DELETE, auth(trainer), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_reports WHERE id = ?", Long.class, reportId);
        assertThat(count).isZero();
    }

    @Test
    void clientCannotSetFeedback_returnsForbidden() {
        Long reportId = createReportAndGetId();

        Map<String, String> feedback = Map.of("coachFeedback", "intento");

        ResponseEntity<String> response = rest.exchange(
                "/api/reports/" + reportId + "/feedback", HttpMethod.PATCH,
                entity(feedback, client), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clientCannotAccessOtherClientReports_returnsForbidden() {
        Long otherClientId = createClient(trainer, "pedro", "pedro@mail.com", "pedro1234");

        ResponseEntity<String> response = rest.exchange(
                "/api/clients/" + otherClientId + "/reports", HttpMethod.GET, auth(client), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidReport_returnsBadRequest() {
        Map<String, Object> invalid = Map.of("weight", 5, "energyLevel", 99);

        ResponseEntity<String> response = rest.exchange(
                "/api/clients/" + clientId + "/reports", HttpMethod.POST,
                entity(invalid, client), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_reports WHERE client_id = ?", Long.class, clientId);
        assertThat(count).isZero();
    }

    @Test
    void unauthenticatedRequest_returnsUnauthorized() {
        ResponseEntity<String> response = rest.exchange(
                "/api/clients/" + clientId + "/reports", HttpMethod.GET, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private Long createReportAndGetId() {
        ResponseEntity<Map> response = rest.exchange(
                "/api/clients/" + clientId + "/reports", HttpMethod.POST,
                entity(sampleReport(), client), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }
}
