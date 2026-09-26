package com.adrifit.backend.lead;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Contact request → questionnaire → approval → activation (and the rejections). */
class LeadIT extends AbstractIntegrationTest {

    private String trainer;
    private String ip;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
        ip = "10.0." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250); // rate limit is per IP
    }

    @Test
    void fullIntakeFlowCreatesAnActivatedClient() {
        assertThat(contact("Lucía", "lucia@mail.com", "Quiero perder 6 kg antes del verano").getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        // Prospect gets a confirmation, the trainer a notification; the request shows up with a badge.
        assertThat(emails("lucia@mail.com")).extracting(e -> e.get("subject")).contains("Hemos recibido tu solicitud");
        assertThat(emails("trainer@adrifit.com")).extracting(e -> (String) e.get("subject")).anyMatch(s -> s.contains("Nueva solicitud de Lucía"));
        assertThat(get("/api/leads/counts", trainer).getBody()).containsEntry("newRequests", 1);
        assertThat(get("/api/dashboard/trainer", trainer).getBody()).containsEntry("pendingLeads", 1);
        Long leadId = leadId();

        // 1st filter: send the questionnaire with a personal message.
        ResponseEntity<Map<String, Object>> sent = post("/api/leads/" + leadId + "/questionnaire",
                Map.of("message", "¡Me encaja tu objetivo!"), trainer);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        String html = lastEmailBody("lucia@mail.com");
        assertThat(html).contains("Me encaja tu objetivo!");
        String token = extract(html, "/cuestionario/([A-Za-z0-9_-]+)");

        Map<String, Object> info = get("/api/public/questionnaire/" + token, null).getBody();
        assertThat(info).containsEntry("state", "OPEN").containsEntry("firstName", "Lucía");

        // Injuries ticked but not described → rejected by validation.
        Map<String, Object> answers = questionnaire();
        answers.put("hasInjuries", true);
        assertThat(post("/api/public/questionnaire/" + token, answers, null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        answers.put("injuries", "Condromalacia rotuliana en la rodilla derecha");
        answers.put("healthConsent", false);
        assertThat(post("/api/public/questionnaire/" + token, answers, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        answers.put("healthConsent", true);
        assertThat(post("/api/public/questionnaire/" + token, answers, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Link can't be reused; trainer notified with the health warning.
        assertThat(get("/api/public/questionnaire/" + token, null).getBody()).containsEntry("state", "COMPLETED");
        assertThat(post("/api/public/questionnaire/" + token, answers, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(lastEmailBody("trainer@adrifit.com")).contains("lesiones o condiciones médicas");
        Map<String, Object> detail = get("/api/leads/" + leadId, trainer).getBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> lead = (Map<String, Object>) detail.get("lead");
        assertThat(lead).containsEntry("status", "QUESTIONNAIRE_COMPLETED").containsEntry("healthFlag", true);
        assertThat(detail.get("questionnairePlanName")).isEqualTo("Premium");

        // Approve → client + subscription + activation email (no temporary password by email).
        Map<String, Object> approve = new HashMap<>();
        approve.put("planId", planId("Premium"));
        approve.put("billingPeriod", "QUARTERLY");
        approve.put("message", "Empezamos el lunes 💪");
        ResponseEntity<Map<String, Object>> approved = post("/api/leads/" + leadId + "/approve", approve, trainer);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody()).containsEntry("activationPending", true);
        Long clientId = count("SELECT client_id FROM leads WHERE id = ?", leadId);
        String activationHtml = lastEmailBody("lucia@mail.com");
        assertThat(activationHtml).contains("Activar mi cuenta", "Empezamos el lunes", "Premium").doesNotContain("Contraseña temporal");
        String activationToken = extract(activationHtml, "/activar/([A-Za-z0-9_-]+)");
        assertThat(count("SELECT COUNT(*) FROM subscriptions WHERE client_id = ? AND billing_period = 'QUARTERLY'", clientId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT notes FROM clients WHERE id = ?", String.class, clientId)).contains("Condromalacia");

        // Activation: choose password, get signed in, can't reuse the link.
        Map<String, Object> act = get("/api/public/activation/" + activationToken, null).getBody();
        assertThat(act).containsEntry("firstName", "Lucía");
        assertThat(post("/api/public/activation/" + activationToken, Map.of("password", "corta"), null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map<String, Object>> activated = post("/api/public/activation/" + activationToken,
                Map.of("password", "MiClaveSegura1"), null);
        assertThat(activated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activated.getBody()).containsEntry("mustChangePassword", false).containsEntry("role", "CLIENT");
        String clientToken = (String) activated.getBody().get("token");
        assertThat(get("/api/auth/me", clientToken).getBody()).containsEntry("clientId", clientId.intValue());
        assertThat(login((String) act.get("username"), "MiClaveSegura1")).isNotBlank();
        assertThat(get("/api/public/activation/" + activationToken, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/leads/" + leadId + "/resend-activation", Map.of(), trainer).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // The questionnaire stays in the client file.
        assertThat(get("/api/clients/" + clientId + "/questionnaire", trainer).getBody())
                .containsEntry("mainGoal", "PERDER_GRASA").containsEntry("injuries", "Condromalacia rotuliana en la rodilla derecha");
        assertThat(get("/api/leads/counts", trainer).getBody()).containsEntry("accepted", 1).containsEntry("newRequests", 0);
    }

    @Test
    void rejectAfterRequestAndAfterQuestionnaire() {
        contact("Pedro", "pedro@mail.com", "Quiero ganar masa muscular");
        Long first = leadId();
        ResponseEntity<Map<String, Object>> rejected = post("/api/leads/" + first + "/reject",
                Map.of("message", "Ahora mismo tengo la agenda completa"), trainer);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastEmailBody("pedro@mail.com")).contains("Lo sentimos", "agenda completa");
        assertThat(post("/api/leads/" + first + "/questionnaire", Map.of(), trainer).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        contact("Marta", "marta@mail.com", "Mejorar mi salud y mi energía");
        Long second = leadId("marta@mail.com");
        post("/api/leads/" + second + "/questionnaire", Map.of(), trainer);
        String token = extract(lastEmailBody("marta@mail.com"), "/cuestionario/([A-Za-z0-9_-]+)");
        post("/api/public/questionnaire/" + token, questionnaire(), null);
        assertThat(post("/api/leads/" + second + "/reject", Map.of(), trainer).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastEmailBody("marta@mail.com")).contains("Lo sentimos");
        assertThat(get("/api/public/questionnaire/" + token, null).getBody()).containsEntry("state", "CLOSED");
        assertThat(count("SELECT COUNT(*) FROM clients")).isZero();

        // GDPR: the trainer can delete the request with its health data.
        assertThat(delete("/api/leads/" + second, trainer).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(count("SELECT COUNT(*) FROM leads WHERE id = ?", second)).isZero();
    }

    @Test
    void spamDuplicatesAndExistingClientsAreHandled() {
        // Honeypot filled → silently ignored.
        Map<String, Object> bot = contactBody("Bot", "bot@spam.com", "Compra seguidores baratos ya");
        bot.put("website", "http://spam.example");
        assertThat(postWithIp("/api/public/leads", bot).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(count("SELECT COUNT(*) FROM leads")).isZero();

        // Same email with an open request → not duplicated.
        contact("Ana", "ana@mail.com", "Quiero volver a entrenar");
        contact("Ana", "ANA@mail.com", "Quiero volver a entrenar (2)");
        assertThat(count("SELECT COUNT(*) FROM leads")).isEqualTo(1);

        // Already a client → no request, friendly email instead.
        createClient(trainer, "laura", "laura@mail.com", "laura12345");
        contact("Laura", "laura@mail.com", "Quiero cambiar de plan");
        assertThat(count("SELECT COUNT(*) FROM leads WHERE email = 'laura@mail.com'")).isZero();
        assertThat(lastEmailBody("laura@mail.com")).contains("Ya tienes cuenta");

        // Consent is mandatory; rate limit per IP.
        Map<String, Object> noConsent = contactBody("Sin", "sin@mail.com", "Objetivo sin consentimiento");
        noConsent.put("consent", false);
        assertThat(postWithIp("/api/public/leads", noConsent).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        int accepted = 0;
        for (int i = 0; i < 11; i++) {
            if (contact("Flood" + i, "flood" + i + "@mail.com", "Mensaje número " + i).getStatusCode() == HttpStatus.ACCEPTED) {
                accepted++;
            }
        }
        assertThat(accepted).isLessThan(11);
    }

    @Test
    void expiredQuestionnaireLinkIsRefused() {
        contact("Iván", "ivan@mail.com", "Preparar una carrera de 10 km");
        Long id = leadId();
        post("/api/leads/" + id + "/questionnaire", Map.of(), trainer);
        String token = extract(lastEmailBody("ivan@mail.com"), "/cuestionario/([A-Za-z0-9_-]+)");
        jdbc.update("UPDATE leads SET questionnaire_expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), id);
        assertThat(get("/api/public/questionnaire/" + token, null).getBody()).containsEntry("state", "EXPIRED");
        assertThat(post("/api/public/questionnaire/" + token, questionnaire(), null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get("/api/public/questionnaire/not-a-token", null).getBody()).containsEntry("state", "CLOSED");

        // Resend: new link works, old one doesn't.
        post("/api/leads/" + id + "/questionnaire", Map.of(), trainer);
        String fresh = extract(lastEmailBody("ivan@mail.com"), "/cuestionario/([A-Za-z0-9_-]+)");
        assertThat(fresh).isNotEqualTo(token);
        assertThat(get("/api/public/questionnaire/" + fresh, null).getBody()).containsEntry("state", "OPEN");
        assertThat(get("/api/public/questionnaire/" + token, null).getBody()).containsEntry("state", "CLOSED");
        // Public endpoints never expose trainer data.
        assertThat(rest.exchange("/api/leads", HttpMethod.GET, auth(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> contact(String name, String email, String objective) {
        return postWithIp("/api/public/leads", contactBody(name, email, objective));
    }

    private Map<String, Object> contactBody(String name, String email, String objective) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", name);
        body.put("lastName", "Prueba");
        body.put("email", email);
        body.put("phone", "600123123");
        body.put("objective", objective);
        body.put("planId", planId("Premium"));
        body.put("consent", true);
        return body;
    }

    private ResponseEntity<Map<String, Object>> postWithIp(String url, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), MAP);
    }

    private Map<String, Object> questionnaire() {
        Map<String, Object> q = new HashMap<>();
        q.put("birthDate", "1995-05-20");
        q.put("sex", "MUJER");
        q.put("heightCm", 165);
        q.put("weightKg", 68.5);
        q.put("activityLevel", "LIGERO");
        q.put("mainGoal", "PERDER_GRASA");
        q.put("experience", "MENOS_1");
        q.put("hasInjuries", false);
        q.put("hasMedicalConditions", false);
        q.put("daysPerWeek", 4);
        q.put("minutesPerSession", 60);
        q.put("trainingPlace", "GIMNASIO");
        q.put("dietType", "OMNIVORA");
        q.put("sleepHours", 7);
        q.put("stressLevel", 3);
        q.put("planId", planId("Premium"));
        q.put("billingPeriod", "MONTHLY");
        q.put("healthConsent", true);
        return q;
    }

    private Long leadId() {
        return count("SELECT MAX(id) FROM leads");
    }

    private Long leadId(String email) {
        return count("SELECT MAX(id) FROM leads WHERE email = ?", email);
    }

    private List<Map<String, Object>> emails(String to) {
        return jdbc.queryForList("SELECT subject, html_body FROM email_messages WHERE to_address = ? ORDER BY id", to);
    }

    private String lastEmailBody(String to) {
        return jdbc.queryForObject("SELECT html_body FROM email_messages WHERE to_address = ? ORDER BY id DESC LIMIT 1", String.class, to);
    }

    private static String extract(String html, String regex) {
        Matcher m = Pattern.compile(regex).matcher(html);
        assertThat(m.find()).as("link in email").isTrue();
        return m.group(1);
    }

    @SuppressWarnings("unused")
    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
