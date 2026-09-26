package com.adrifit.backend.support;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    protected static final String TRAINER_USERNAME = "trainer";
    protected static final String TRAINER_PASSWORD = "trainer123";

    /** Child tables first so foreign keys never block the cleanup. */
    private static final List<String> TABLES = List.of(
            "leads", "technique_videos", "push_subscriptions", "passkey_credentials", "messages", "payments", "email_messages", "trainer_tasks", "testimonials", "progress_photos",
            "workout_log_sets", "workout_logs", "client_analyses", "weekly_reports", "client_diets",
            "client_workouts", "subscriptions", "workout_exercises", "workouts", "diet_alternatives",
            "diet_foods", "diet_meals", "diet_days", "diets", "clients");

    protected static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    protected static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        TABLES.forEach(t -> jdbc.execute("DELETE FROM " + t));
        jdbc.update("DELETE FROM users WHERE role <> 'TRAINER'");
        jdbc.update("DELETE FROM plans WHERE name NOT IN ('Básica', 'Premium')");
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
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    protected HttpEntity<Object> entity(Object body, String token) {
        return new HttpEntity<>(body, bearer(token));
    }

    protected HttpEntity<Void> auth(String token) {
        return new HttpEntity<>(bearer(token));
    }

    protected Long planId(String name) {
        return jdbc.queryForObject("SELECT id FROM plans WHERE name = ?", Long.class, name);
    }

    /**
     * Creates a client through the REST API on the Premium plan, then fixes its username and
     * password so tests can log in with known credentials.
     */
    protected Long createClient(String trainerToken, String username, String email, String password) {
        return createClient(trainerToken, username, email, password, "Premium");
    }

    protected Long createClient(String trainerToken, String username, String email, String password, String planName) {
        Map<String, Object> body = Map.of(
                "firstName", "Test",
                "lastName", "User",
                "phone", "600000000",
                "birthDate", "1990-01-01",
                "objective", "Get fit",
                "email", email,
                "planId", planId(planName));
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/api/clients", HttpMethod.POST, entity(body, trainerToken), MAP);
        if (response.getStatusCode() != HttpStatus.CREATED) {
            throw new IllegalStateException("Client creation failed: " + response.getStatusCode() + " " + response.getBody());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> client = (Map<String, Object>) response.getBody().get("client");
        Long clientId = ((Number) client.get("id")).longValue();
        Long userId = ((Number) client.get("userId")).longValue();
        jdbc.update("UPDATE users SET username = ?, password = ?, must_change_password = FALSE WHERE id = ?",
                username, passwordEncoder.encode(password), userId);
        return clientId;
    }

    /** Creates an extra plan (e.g. without chat or PDF) to test plan-gated features. */
    protected Long createPlan(String trainerToken, String name, boolean messaging, boolean pdf) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("monthlyPrice", 50);
        body.put("reviewFrequencyDays", 30);
        body.put("messagingEnabled", messaging);
        body.put("analyticsEnabled", false);
        body.put("pdfExportEnabled", pdf);
        body.put("prioritySupport", false);
        return id(post("/api/plans", body, trainerToken));
    }

    /** Client check-in through the real multipart endpoint with {@code photos} PNG files. */
    protected ResponseEntity<Map<String, Object>> submitReport(String token, double weight, String comments, int photos) {
        org.springframework.util.MultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        body.add("weight", String.valueOf(weight));
        if (comments != null) {
            body.add("comments", comments);
        }
        for (int i = 0; i < photos; i++) {
            final int n = i;
            HttpHeaders part = new HttpHeaders();
            part.setContentType(MediaType.IMAGE_PNG);
            body.add("files", new HttpEntity<>(new org.springframework.core.io.ByteArrayResource(png()) {
                @Override
                public String getFilename() {
                    return "foto" + n + ".png";
                }
            }, part));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange("/api/reports", HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }

    protected static byte[] png() {
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── small HTTP helpers ──────────────────────────────────────────────────

    protected ResponseEntity<Map<String, Object>> get(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, auth(token), MAP);
    }

    protected ResponseEntity<List<Map<String, Object>>> getList(String url, String token) {
        return rest.exchange(url, HttpMethod.GET, auth(token), LIST);
    }

    protected ResponseEntity<Map<String, Object>> post(String url, Object body, String token) {
        return rest.exchange(url, HttpMethod.POST, entity(body, token), MAP);
    }

    protected ResponseEntity<Map<String, Object>> put(String url, Object body, String token) {
        return rest.exchange(url, HttpMethod.PUT, entity(body, token), MAP);
    }

    protected ResponseEntity<Map<String, Object>> patch(String url, Object body, String token) {
        return rest.exchange(url, HttpMethod.PATCH, entity(body, token), MAP);
    }

    protected ResponseEntity<String> delete(String url, String token) {
        return rest.exchange(url, HttpMethod.DELETE, auth(token), String.class);
    }

    protected static Long id(ResponseEntity<Map<String, Object>> response) {
        return ((Number) response.getBody().get("id")).longValue();
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
