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
    void trainerCreatesClient_persistsUserAndClient() {
        String token = trainerToken();

        Long clientId = createClient(token, "ana", "ana@mail.com", "ana12345");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT first_name, last_name, user_id FROM clients WHERE id = ?", clientId);
        assertThat(row.get("first_name")).isEqualTo("Test");

        Long userId = ((Number) row.get("user_id")).longValue();
        String role = jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
        assertThat(role).isEqualTo("CLIENT");

        String storedPassword = jdbc.queryForObject(
                "SELECT password FROM users WHERE id = ?", String.class, userId);
        assertThat(storedPassword).startsWith("$2"); // BCrypt hash, not plaintext
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
    void duplicateUsername_returnsConflict() {
        String token = trainerToken();
        createClient(token, "ana", "ana@mail.com", "ana12345");

        Map<String, String> body = Map.of(
                "username", "ana",
                "email", "other@mail.com",
                "password", "ana12345",
                "firstName", "Ana",
                "lastName", "Dup");

        ResponseEntity<String> response = rest.exchange(
                "/api/clients", HttpMethod.POST, entity(body, token), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
