package com.adrifit.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthControllerIT extends AbstractIntegrationTest {

    @Test
    void login_withValidTrainerCredentials_returnsToken() {
        Map<String, String> body = Map.of("username", TRAINER_USERNAME, "password", TRAINER_PASSWORD);

        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("token");
        assertThat(response.getBody().get("role")).isEqualTo("TRAINER");
    }

    @Test
    void login_withInvalidCredentials_returnsUnauthorized() {
        Map<String, String> body = Map.of("username", TRAINER_USERNAME, "password", "wrong");

        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_returnsClientProfileIds() {
        Long clientId = createClient(trainerToken(), "ana", "ana@mail.com", "ana12345");

        ResponseEntity<Map<String, Object>> me = get("/api/auth/me", login("ana", "ana12345"));

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("role")).isEqualTo("CLIENT");
        assertThat(((Number) me.getBody().get("clientId")).longValue()).isEqualTo(clientId);
    }

    @Test
    void changePassword_requiresCurrentPasswordAndClearsFlag() {
        createClient(trainerToken(), "ana", "ana@mail.com", "ana12345");
        jdbc.update("UPDATE users SET must_change_password = TRUE WHERE username = 'ana'");
        String token = login("ana", "ana12345");

        ResponseEntity<Map<String, Object>> wrong = post("/api/auth/change-password",
                Map.of("currentPassword", "nope", "newPassword", "nuevaClave1"), token);
        assertThat(wrong.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Map<String, Object>> tooShort = post("/api/auth/change-password",
                Map.of("currentPassword", "ana12345", "newPassword", "corta"), token);
        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> ok = post("/api/auth/change-password",
                Map.of("currentPassword", "ana12345", "newPassword", "nuevaClave1"), token);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login("ana", "nuevaClave1")).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT must_change_password FROM users WHERE username = 'ana'", Boolean.class)).isFalse();
    }

    @Test
    void unknownEndpoint_returnsNotFoundNotServerError() {
        ResponseEntity<Map<String, Object>> response = get("/api/does-not-exist", trainerToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
