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

        Long trainerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ? AND role = 'TRAINER'",
                Long.class, TRAINER_USERNAME);
        assertThat(trainerCount).isEqualTo(1L);
    }

    @Test
    void login_withInvalidCredentials_returnsUnauthorized() {
        Map<String, String> body = Map.of("username", TRAINER_USERNAME, "password", "wrong");

        ResponseEntity<Map> response = rest.postForEntity("/api/auth/login", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
