package com.adrifit.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.jobs.DailyJobs;
import com.adrifit.backend.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SubscriptionIT extends AbstractIntegrationTest {

    @Autowired
    private DailyJobs dailyJobs;

    private String trainer;
    private String client;
    private Long clientId;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
        clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345", "Basic");
        client = login("ana", "ana12345");
    }

    @Test
    void clientChangesPlan_oldSubscriptionClosedAndItsUnpaidChargeVoided() {
        ResponseEntity<Map<String, Object>> response = post("/api/subscriptions/me/change-plan",
                Map.of("planId", planId("Premium")), client);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("planName")).isEqualTo("Premium");
        assertThat(count("SELECT COUNT(*) FROM subscriptions WHERE client_id = ?", clientId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM subscriptions WHERE client_id = ? AND active = TRUE", clientId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payments WHERE client_id = ? AND status = 'CANCELLED'", clientId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payments WHERE client_id = ? AND status = 'PENDING' AND amount = 119", clientId))
                .isEqualTo(1);

        ResponseEntity<Map<String, Object>> same = post("/api/subscriptions/me/change-plan",
                Map.of("planId", planId("Premium")), client);
        assertThat(same.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelAtPeriodEnd_thenRenewalJobClosesWithoutCharging() {
        ResponseEntity<Map<String, Object>> cancelled = post("/api/subscriptions/me/cancel", Map.of(), client);
        assertThat(cancelled.getBody().get("cancelAtPeriodEnd")).isEqualTo(true);
        assertThat(cancelled.getBody().get("status")).isEqualTo("ACTIVE");

        jdbc.update("UPDATE subscriptions SET renewal_date = ? WHERE client_id = ?", LocalDate.now(), clientId);
        long paymentsBefore = count("SELECT COUNT(*) FROM payments WHERE client_id = ?", clientId);

        dailyJobs.run(LocalDate.now());

        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE client_id = ?", String.class, clientId))
                .isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM payments WHERE client_id = ?", clientId)).isEqualTo(paymentsBefore);
        assertThat(get("/api/subscriptions/me", client).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Can subscribe again afterwards.
        assertThat(post("/api/subscriptions/me/change-plan", Map.of("planId", planId("Basic")), client).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void resumeAfterCancel() {
        post("/api/subscriptions/me/cancel", Map.of(), client);
        ResponseEntity<Map<String, Object>> resumed = post("/api/subscriptions/me/resume", Map.of(), client);
        assertThat(resumed.getBody().get("cancelAtPeriodEnd")).isEqualTo(false);
    }

    @Test
    void renewalJob_opensNextMonthlyPeriodAndCreatesCharge() {
        LocalDate today = LocalDate.now();
        // Simulate that the first month (already charged) started one month ago.
        jdbc.update("UPDATE subscriptions SET start_date = ?, renewal_date = ? WHERE client_id = ?",
                today.minusMonths(1), today, clientId);
        jdbc.update("UPDATE payments SET period_start = ?, period_end = ?, due_date = ? WHERE client_id = ?",
                today.minusMonths(1), today, today.minusMonths(1), clientId);

        dailyJobs.run(today);

        assertThat(jdbc.queryForObject("SELECT renewal_date FROM subscriptions WHERE client_id = ?", java.sql.Date.class, clientId)
                .toLocalDate()).isEqualTo(today.plusMonths(1));
        assertThat(count("SELECT COUNT(*) FROM payments WHERE client_id = ? AND period_start = ?", clientId, today))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_messages WHERE client_id = ? AND type = 'PAYMENT_DUE'", clientId))
                .isEqualTo(2);
    }

    @Test
    void trainerPausesAndReactivates() {
        ResponseEntity<Map<String, Object>> paused = patch("/api/clients/" + clientId + "/subscription/status",
                Map.of("status", "PAUSED"), trainer);
        assertThat(paused.getBody().get("status")).isEqualTo("PAUSED");

        // Paused: the client can't switch plans on its own.
        assertThat(post("/api/subscriptions/me/change-plan", Map.of("planId", planId("Premium")), client).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> active = patch("/api/clients/" + clientId + "/subscription/status",
                Map.of("status", "ACTIVE"), trainer);
        assertThat(active.getBody().get("status")).isEqualTo("ACTIVE");
    }
}
