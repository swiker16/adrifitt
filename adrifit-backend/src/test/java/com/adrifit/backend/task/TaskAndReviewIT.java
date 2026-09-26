package com.adrifit.backend.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TaskAndReviewIT extends AbstractIntegrationTest {

    private String trainer;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
    }

    @Test
    void taskCrudAndCompletion() {
        ResponseEntity<Map<String, Object>> created = post("/api/tasks",
                Map.of("title", "Preparar dieta de volumen", "priority", "HIGH", "dueDate", LocalDate.now().minusDays(1).toString()),
                trainer);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("overdue")).isEqualTo(true);
        Long id = id(created);

        assertThat(patch("/api/tasks/" + id + "/complete", Map.of(), trainer).getBody().get("status")).isEqualTo("DONE");
        assertThat(getList("/api/tasks?status=PENDING", trainer).getBody()).isEmpty();
        assertThat(patch("/api/tasks/" + id + "/reopen", Map.of(), trainer).getBody().get("status")).isEqualTo("PENDING");
        assertThat(put("/api/tasks/" + id, Map.of("title", "Otra"), trainer).getBody().get("title")).isEqualTo("Otra");
        assertThat(delete("/api/tasks/" + id, trainer).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void reviewScheduleAndDailyJobGeneratesReviewTaskAndReminder() {
        Long clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345", "Premium"); // review every 7 days
        jdbc.update("UPDATE subscriptions SET start_date = ? WHERE client_id = ?", LocalDate.now().minusDays(8), clientId);

        List<Map<String, Object>> schedule = getList("/api/reviews/schedule", trainer).getBody();
        assertThat(schedule).hasSize(1);
        assertThat(schedule.get(0).get("state")).isEqualTo("OVERDUE");

        assertThat(post("/api/jobs/daily/run", Map.of(), trainer).getBody().get("reviewTasksCreated")).isEqualTo(1);
        assertThat(post("/api/jobs/daily/run", Map.of(), trainer).getBody().get("reviewTasksCreated")).as("only once").isEqualTo(0);
        assertThat(count("SELECT COUNT(*) FROM email_messages WHERE client_id = ? AND type = 'REVIEW_REMINDER'", clientId)).isEqualTo(1);

        // The client sends its check-in and the trainer reviews it: the task is closed and the schedule moves.
        String client = login("ana", "ana12345");
        Long reportId = id(submitReport(client, 70, null, 4));
        patch("/api/reports/" + reportId + "/feedback", Map.of("coachFeedback", "Bien"), trainer);

        assertThat(count("SELECT COUNT(*) FROM trainer_tasks WHERE client_id = ? AND status = 'PENDING'", clientId)).isZero();
        assertThat(getList("/api/reviews/schedule", trainer).getBody().get(0).get("nextReviewDate"))
                .isEqualTo(LocalDate.now().plusDays(7).toString());
    }

    @Test
    void clientCannotUseTasks() {
        createClient(trainer, "ana", "ana@mail.com", "ana12345");
        assertThat(get("/api/tasks?status=PENDING", login("ana", "ana12345")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
