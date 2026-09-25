package com.adrifit.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ClientControllerIT extends AbstractIntegrationTest {

    @Test
    void trainerCreatesClient_persistsUserClientSubscriptionChargeAndWelcomeEmail() {
        String token = trainerToken();

        Long clientId = createClient(token, "ana", "ana@mail.com", "ana12345");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT first_name, last_name, user_id, trainer_id FROM clients WHERE id = ?", clientId);
        assertThat(row.get("first_name")).isEqualTo("Test");
        assertThat(row.get("trainer_id")).as("defaults to the authenticated trainer").isNotNull();

        Long userId = ((Number) row.get("user_id")).longValue();
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId)).isEqualTo("CLIENT");
        assertThat(jdbc.queryForObject("SELECT password FROM users WHERE id = ?", String.class, userId)).startsWith("$2");

        // Subscription (monthly) + first pending charge + welcome email captured in test mode.
        Map<String, Object> sub = jdbc.queryForMap(
                "SELECT status, active, start_date, renewal_date FROM subscriptions WHERE client_id = ?", clientId);
        assertThat(sub.get("status")).isEqualTo("ACTIVE");
        assertThat(sub.get("renewal_date").toString())
                .isEqualTo(((java.sql.Date) sub.get("start_date")).toLocalDate().plusMonths(1).toString());
        assertThat(count("SELECT COUNT(*) FROM payments WHERE client_id = ? AND status = 'PENDING'", clientId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM email_messages WHERE client_id = ? AND type = 'WELCOME' AND status = 'TEST_CAPTURED'",
                clientId)).isEqualTo(1);
    }

    @Test
    void newClientMustChangeTemporaryPassword() {
        String token = trainerToken();
        Map<String, Object> body = Map.of("firstName", "Lucía", "lastName", "Pérez", "phone", "611111111",
                "birthDate", "1995-03-03", "objective", "Fuerza", "email", "lucia@mail.com", "planId", planId("Basic"));
        ResponseEntity<Map<String, Object>> created = post("/api/clients", body, token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String username = (String) created.getBody().get("username");
        String tempPassword = (String) created.getBody().get("temporaryPassword");
        assertThat(username).isEqualTo("luciaperez");

        Map<?, ?> login = rest.postForObject("/api/auth/login", Map.of("username", username, "password", tempPassword), Map.class);
        assertThat(login.get("mustChangePassword")).isEqualTo(true);
    }

    @Test
    void clientCannotAccessClientEndpoints_returnsForbidden() {
        String trainer = trainerToken();
        createClient(trainer, "ana", "ana@mail.com", "ana12345");
        String clientToken = login("ana", "ana12345");

        ResponseEntity<String> response = rest.exchange(
                "/api/clients", HttpMethod.GET, auth(clientToken), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void clientReadsOwnProfile() {
        String trainer = trainerToken();
        Long id = createClient(trainer, "ana", "ana@mail.com", "ana12345");

        ResponseEntity<Map<String, Object>> me = get("/api/clients/me", login("ana", "ana12345"));

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) me.getBody().get("id")).longValue()).isEqualTo(id);
    }

    @Test
    void duplicateEmail_returnsConflict() {
        String token = trainerToken();
        createClient(token, "ana", "ana@mail.com", "ana12345");

        Map<String, Object> body = Map.of("firstName", "Ana", "lastName", "Dup", "phone", "600000001",
                "birthDate", "1990-01-01", "objective", "x", "email", "ana@mail.com", "planId", planId("Basic"));

        ResponseEntity<String> response = rest.exchange("/api/clients", HttpMethod.POST, entity(body, token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void notesAreUpdated() {
        String token = trainerToken();
        Long id = createClient(token, "ana", "ana@mail.com", "ana12345");

        ResponseEntity<Map<String, Object>> response = put("/api/clients/" + id,
                Map.of("firstName", "Ana", "lastName", "López", "notes", "Lesión de rodilla"), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT notes FROM clients WHERE id = ?", String.class, id)).isEqualTo("Lesión de rodilla");
    }

    @Test
    void resetPassword_generatesNewTemporaryPasswordAndEmail() {
        String token = trainerToken();
        Long id = createClient(token, "ana", "ana@mail.com", "ana12345");

        ResponseEntity<Map<String, Object>> response = post("/api/clients/" + id + "/reset-password", Map.of(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newPassword = (String) response.getBody().get("temporaryPassword");
        assertThat(login("ana", newPassword)).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM email_messages WHERE client_id = ? AND type = 'PASSWORD_RESET'", id)).isEqualTo(1);
    }

    @Test
    void deleteClient_removesEverythingThatReferencesIt() {
        String trainer = trainerToken();
        Long id = createClient(trainer, "ana", "ana@mail.com", "ana12345");
        String client = login("ana", "ana12345");
        post("/api/reports", Map.of("weight", 70), client);
        post("/api/messages/me", Map.of("content", "hola"), client);
        post("/api/tasks", Map.of("title", "Llamar", "clientId", id), trainer);
        post("/api/testimonials/me", Map.of("rating", 5, "content", "Muy buen entrenador"), client);

        ResponseEntity<String> response = delete("/api/clients/" + id, trainer);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("SELECT COUNT(*) FROM clients WHERE id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE username = 'ana'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM weekly_reports WHERE client_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM payments WHERE client_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM messages WHERE client_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM subscriptions WHERE client_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM trainer_tasks WHERE client_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM testimonials WHERE client_id = ?", id)).isZero();
        assertThat(count("SELECT COUNT(*) FROM email_messages WHERE client_id = ?", id)).isZero();
    }
}
