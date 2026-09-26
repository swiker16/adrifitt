package com.adrifit.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.jobs.DailyJobs;
import com.adrifit.backend.notification.service.WebPushSender;
import com.adrifit.backend.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Which events notify whom, with a recording push service instead of the real one. */
@Import(PushNotificationIT.RecordingSenderConfig.class)
class PushNotificationIT extends AbstractIntegrationTest {

    record Sent(String endpoint, String payload) {
    }

    static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    /** Endpoints the fake push service answers 410 Gone for (unsubscribed browser). */
    static final List<String> GONE = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingSenderConfig {
        @Bean
        @Primary
        WebPushSender recordingSender() {
            return (endpoint, p256dh, auth, payload) -> {
                SENT.add(new Sent(endpoint, payload));
                return GONE.contains(endpoint) ? 410 : 201;
            };
        }
    }

    @Autowired
    private DailyJobs dailyJobs;

    private String trainer;
    private String client;
    private Long clientId;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM push_subscriptions");
        SENT.clear();
        GONE.clear();
        trainer = trainerToken();
        clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345");
        client = login("ana", "ana12345");
    }

    private void subscribe(String token, String endpoint) {
        ResponseEntity<Map<String, Object>> r = post("/api/push/subscriptions", Map.of(
                "endpoint", endpoint,
                "keys", Map.of("p256dh", "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM", "auth", "tBHItJI5svbpez7KI4CCXg"),
                "userAgent", "Mozilla/5.0 (iPhone)"), token);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("subscribed")).isEqualTo(true);
    }

    private List<Sent> waitFor(String endpoint, int count) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<Sent> matching = SENT.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
            if (matching.size() >= count) {
                return matching;
            }
            Thread.sleep(100);
        }
        return SENT.stream().filter(s -> s.endpoint().equals(endpoint)).toList();
    }

    @Test
    void publicKeyAndSubscriptionLifecycle() {
        String key = (String) get("/api/push/public-key", client).getBody().get("publicKey");
        assertThat(key).hasSize(87); // 65-byte uncompressed P-256 point in base64url
        assertThat(get("/api/push/public-key", client).getBody().get("publicKey")).isEqualTo(key); // stable

        subscribe(client, "https://push.example/client-phone");
        assertThat(get("/api/push/status?endpoint=https://push.example/client-phone", client).getBody().get("devices")).isEqualTo(1);

        post("/api/push/subscriptions/remove", Map.of("endpoint", "https://push.example/client-phone"), client);
        assertThat(get("/api/push/status?endpoint=https://push.example/client-phone", client).getBody().get("subscribed")).isEqualTo(false);
        assertThat(post("/api/push/subscriptions", Map.of("endpoint", "http://insecure", "keys", Map.of("p256dh", "x", "auth", "y")), client)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void chatNotifiesTheOtherSideWithDeepLink() throws Exception {
        subscribe(client, "https://push.example/client-phone");
        subscribe(trainer, "https://push.example/trainer-laptop");

        post("/api/messages/clients/" + clientId, Map.of("content", "Sube 2,5 kg en sentadilla"), trainer);
        List<Sent> toClient = waitFor("https://push.example/client-phone", 1);
        assertThat(toClient).hasSize(1);
        assertThat(toClient.get(0).payload()).contains("Mensaje de tu entrenador", "Sube 2,5 kg", "/client/messages", "navigateLastFocusedOrOpen");

        post("/api/messages/me", Map.of("content", "¡Hecho!"), client);
        List<Sent> toTrainer = waitFor("https://push.example/trainer-laptop", 1);
        assertThat(toTrainer.get(0).payload()).contains("Test", "¡Hecho!", "/trainer/messages?clientId=" + clientId);
    }

    @Test
    void paymentsReportsAndReviewsNotify() throws Exception {
        subscribe(client, "https://push.example/client-phone");
        subscribe(trainer, "https://push.example/trainer-laptop");

        post("/api/payments", Map.of("clientId", clientId, "amount", 20, "concept", "Sesión extra"), trainer);
        assertThat(waitFor("https://push.example/client-phone", 1).get(0).payload()).contains("Nuevo pago pendiente", "20,00");

        Long paymentId = jdbc.queryForObject("SELECT MAX(id) FROM payments WHERE client_id = ?", Long.class, clientId);
        post("/api/payments/me/" + paymentId + "/bizum", Map.of("phone", "612345678"), client);
        assertThat(waitFor("https://push.example/trainer-laptop", 1).get(0).payload()).contains("Pago recibido", "Bizum");

        submitReport(client, 70.5, "ok", 4);
        assertThat(waitFor("https://push.example/trainer-laptop", 2).get(1).payload()).contains("Nuevo seguimiento", "/trainer/reports");

        // Review due (Premium: every 7 days) → reminder to the client.
        jdbc.update("UPDATE subscriptions SET start_date = ? WHERE client_id = ?", LocalDate.now().minusDays(8), clientId);
        jdbc.update("DELETE FROM weekly_reports WHERE client_id = ?", clientId);
        dailyJobs.run(LocalDate.now());
        assertThat(waitFor("https://push.example/client-phone", 2).stream().map(Sent::payload))
                .anyMatch(p -> p.contains("Toca revisión"));
    }

    @Test
    void overduePaymentReminderAndGoneSubscriptionsAreRemoved() throws Exception {
        subscribe(client, "https://push.example/old-phone");
        GONE.add("https://push.example/old-phone");
        jdbc.update("UPDATE payments SET due_date = ? WHERE client_id = ?", LocalDate.now().minusDays(3), clientId);

        dailyJobs.run(LocalDate.now());

        assertThat(waitFor("https://push.example/old-phone", 1).get(0).payload()).contains("pago vencido", "3 días");
        for (int i = 0; i < 30 && count("SELECT COUNT(*) FROM push_subscriptions") > 0; i++) Thread.sleep(100);
        assertThat(count("SELECT COUNT(*) FROM push_subscriptions")).isZero();
    }

    @Test
    void testNotificationEndpoint() {
        subscribe(client, "https://push.example/client-phone");
        assertThat(post("/api/push/test", Map.of(), client).getBody().get("delivered")).isEqualTo(1);
    }
}
