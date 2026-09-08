package com.adrifit.backend.support;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String TRAINER_USERNAME = "trainer";
    protected static final String TRAINER_PASSWORD = "trainer123";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("DELETE FROM weekly_reports");
        jdbc.execute("DELETE FROM clients");
        jdbc.update("DELETE FROM users WHERE role <> 'TRAINER'");
    }

    protected String login(String username, String password) {
        Map<String, String> body = Map.of("username", username, "password", password);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject("/api/auth/login", body, Map.class);
        return (String) response.get("token");
    }

    protected String trainerToken() {
        return login(TRAINER_USERNAME, TRAINER_PASSWORD);
    }

    protected HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    protected HttpEntity<Object> entity(Object body, String token) {
        return new HttpEntity<>(body, bearer(token));
    }

    protected HttpEntity<Void> auth(String token) {
        return new HttpEntity<>(bearer(token));
    }

    /**
     * Creates a CLIENT user + client profile through the REST API and returns the client id.
     */
    protected Long createClient(String trainerToken, String username, String email, String password) {
        Map<String, String> body = Map.of(
                "username", username,
                "email", email,
                "password", password,
                "firstName", "Test",
                "lastName", "User",
                "phone", "600000000",
                "objective", "Get fit");
        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.exchange(
                "/api/clients", HttpMethod.POST, entity(body, trainerToken), Map.class).getBody();
        return ((Number) response.get("id")).longValue();
    }
}
