package com.adrifit.backend.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MessageIT extends AbstractIntegrationTest {

    private String trainer;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
    }

    @Test
    void premiumClientAndTrainerChat_withUnreadCounters() {
        Long clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345", "Premium");
        String client = login("ana", "ana12345");

        ResponseEntity<Map<String, Object>> sent = post("/api/messages/me", Map.of("content", "¿Puedo cambiar el arroz?"), client);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) get("/api/messages/unread-count", trainer).getBody().get("unread")).intValue()).isEqualTo(1);

        List<Map<String, Object>> inbox = getList("/api/messages/conversations", trainer).getBody();
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("unreadCount")).isEqualTo(1);
        assertThat(inbox.get(0).get("prioritySupport")).isEqualTo(true);

        post("/api/messages/clients/" + clientId + "/read", Map.of(), trainer);
        assertThat(((Number) get("/api/messages/unread-count", trainer).getBody().get("unread")).intValue()).isZero();

        assertThat(post("/api/messages/clients/" + clientId, Map.of("content", "Sí, por patata"), trainer).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) get("/api/messages/me/unread-count", client).getBody().get("unread")).intValue()).isEqualTo(1);

        Map<String, Object> mine = get("/api/messages/me", client).getBody();
        assertThat(mine.get("enabled")).isEqualTo(true);
        assertThat((List<?>) mine.get("messages")).hasSize(2);
    }

    @Test
    void planWithoutMessagingCannotMessage_eitherSide() {
        createPlan(trainer, "Sin chat", false, false);
        Long clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345", "Sin chat");
        String client = login("ana", "ana12345");

        ResponseEntity<Map<String, Object>> mine = get("/api/messages/me", client);
        assertThat(mine.getBody().get("enabled")).isEqualTo(false);

        ResponseEntity<Map<String, Object>> send = post("/api/messages/me", Map.of("content", "hola"), client);
        assertThat(send.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(send.getBody().get("message")).isEqualTo("Tu plan actual no incluye mensajería con el entrenador");

        assertThat(post("/api/messages/clients/" + clientId, Map.of("content", "hola"), trainer).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getList("/api/messages/conversations", trainer).getBody()).isEmpty();
    }

    @Test
    void emptyMessage_isRejected() {
        createClient(trainer, "ana", "ana@mail.com", "ana12345", "Premium");
        assertThat(post("/api/messages/me", Map.of("content", "   "), login("ana", "ana12345")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
