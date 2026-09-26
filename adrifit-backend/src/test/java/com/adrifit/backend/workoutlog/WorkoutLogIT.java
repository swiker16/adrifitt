package com.adrifit.backend.workoutlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class WorkoutLogIT extends AbstractIntegrationTest {

    private String trainer;
    private String client;
    private Long clientId;
    private Long workoutId;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
        clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345");
        client = login("ana", "ana12345");

        Map<String, Object> workout = Map.of(
                "name", "Torso/Pierna",
                "daysPerWeek", 2,
                "exercises", List.of(
                        Map.of("exerciseName", "Press banca", "sets", 3, "reps", 8, "orderIndex", 0, "dayNumber", 1, "dayName", "Torso"),
                        Map.of("exerciseName", "Remo", "sets", 2, "reps", 10, "orderIndex", 1, "dayNumber", 1, "dayName", "Torso"),
                        Map.of("exerciseName", "Sentadilla", "sets", 4, "reps", 6, "orderIndex", 2, "dayNumber", 2, "dayName", "Pierna")));
        workoutId = id(post("/api/workouts", workout, trainer));
        post("/api/workouts/clients/" + clientId + "/assign", Map.of("workoutId", workoutId), trainer);
    }

    @Test
    void templateMapsEverySetOfTheSelectedDay() {
        List<Map<String, Object>> days = getList("/api/workout-logs/me/days", client).getBody();
        assertThat(days).hasSize(2);
        assertThat(days.get(0).get("dayName")).isEqualTo("Torso");
        assertThat(days.get(0).get("totalSets")).isEqualTo(5);

        Map<String, Object> template = get("/api/workout-logs/me/template?dayNumber=1", client).getBody();
        List<Map<String, Object>> exercises = exercises(template);
        assertThat(exercises).hasSize(2);
        assertThat(exercises.get(0).get("exerciseName")).isEqualTo("Press banca");
        assertThat(exercises.get(0).get("sets")).isEqualTo(3);
    }

    @Test
    void logDay_thenProgressAndPreviousValues() {
        ResponseEntity<Map<String, Object>> created = post("/api/workout-logs/me", logRequest(LocalDate.now().minusDays(7), 60), client);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("totalSets")).isEqualTo(5);
        post("/api/workout-logs/me", logRequest(LocalDate.now(), 65), client);

        // previous values pre-filled from the last session of that day
        Map<String, Object> template = get("/api/workout-logs/me/template?dayNumber=1", client).getBody();
        List<?> previous = (List<?>) exercises(template).get(0).get("previous");
        assertThat(previous).hasSize(3);

        List<Map<String, Object>> progress = getList("/api/workout-logs/me/progress", client).getBody();
        Map<String, Object> bench = progress.stream().filter(p -> p.get("exerciseName").equals("Press banca")).findFirst().orElseThrow();
        List<Map<String, Object>> points = (List<Map<String, Object>>) bench.get("points");
        assertThat(points).hasSize(2);
        assertThat(((Number) points.get(1).get("maxWeightKg")).doubleValue()).isEqualTo(65.0);

        assertThat(getList("/api/clients/" + clientId + "/workout-logs", trainer).getBody()).hasSize(2);
    }

    @Test
    void validationRules() {
        Map<String, Object> template = get("/api/workout-logs/me/template?dayNumber=1", client).getBody();
        Long benchId = ((Number) exercises(template).get(0).get("exerciseId")).longValue();

        // set 4 does not exist (bench has 3 sets)
        Map<String, Object> tooManySets = Map.of("performedOn", LocalDate.now().toString(), "dayNumber", 1,
                "sets", List.of(Map.of("exerciseId", benchId, "setNumber", 4, "weightKg", 50, "rir", 2)));
        assertThat(post("/api/workout-logs/me", tooManySets, client).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // missing RIR
        Map<String, Object> noRir = Map.of("performedOn", LocalDate.now().toString(), "dayNumber", 1,
                "sets", List.of(Map.of("exerciseId", benchId, "setNumber", 1, "weightKg", 50)));
        assertThat(post("/api/workout-logs/me", noRir, client).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // future date
        assertThat(post("/api/workout-logs/me", logRequest(LocalDate.now().plusDays(1), 50), client).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // same day twice on the same date
        assertThat(post("/api/workout-logs/me", logRequest(LocalDate.now(), 50), client).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("/api/workout-logs/me", logRequest(LocalDate.now(), 50), client).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateAndDeleteOwnLog_othersCannot() {
        Long logId = id(post("/api/workout-logs/me", logRequest(LocalDate.now(), 50), client));

        ResponseEntity<Map<String, Object>> updated = put("/api/workout-logs/me/" + logId, logRequest(LocalDate.now(), 55), client);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) updated.getBody().get("totalVolumeKg")).doubleValue()).isGreaterThan(0);

        createClient(trainer, "otro", "otro@mail.com", "otro12345");
        String other = login("otro", "otro12345");
        assertThat(delete("/api/workout-logs/me/" + logId, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/workout-logs/me/" + logId, client).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void clientWithoutRoutine_gets404() {
        createClient(trainer, "otro", "otro@mail.com", "otro12345");
        assertThat(get("/api/workout-logs/me/template?dayNumber=1", login("otro", "otro12345")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Map<String, Object> logRequest(LocalDate date, double benchWeight) {
        Map<String, Object> template = get("/api/workout-logs/me/template?dayNumber=1", client).getBody();
        List<Map<String, Object>> sets = new ArrayList<>();
        for (Map<String, Object> ex : exercises(template)) {
            int n = ((Number) ex.get("sets")).intValue();
            for (int s = 1; s <= n; s++) {
                sets.add(Map.of("exerciseId", ex.get("exerciseId"), "setNumber", s,
                        "weightKg", ex.get("exerciseName").equals("Press banca") ? benchWeight : 40, "reps", 8, "rir", 2));
            }
        }
        return Map.of("performedOn", date.toString(), "dayNumber", 1, "notes", "ok", "sets", sets);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> exercises(Map<String, Object> template) {
        return (List<Map<String, Object>>) template.get("exercises");
    }
}
