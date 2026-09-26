package com.adrifit.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PaymentIT extends AbstractIntegrationTest {

    private String trainer;
    private String client;
    private Long clientId;
    private Long paymentId;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
        clientId = createClient(trainer, "ana", "ana@mail.com", "ana12345");
        client = login("ana", "ana12345");
        paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE client_id = ?", Long.class, clientId);
    }

    private Map<String, Object> card(String number) {
        return Map.of("cardNumber", number, "expMonth", 12, "expYear", LocalDate.now().getYear() + 2,
                "cvc", "123", "holderName", "Ana Test");
    }

    @Test
    void clientSeesOwnPendingCharge() {
        ResponseEntity<List<Map<String, Object>>> mine = getList("/api/payments/me", client);

        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).hasSize(1);
        assertThat(mine.getBody().get(0).get("status")).isEqualTo("PENDING");
        assertThat(mine.getBody().get(0).get("amount")).isEqualTo(143.0);
    }

    @Test
    void payWithValidCard_marksPaidStoresOnlyLast4AndEmailsReceipt() {
        ResponseEntity<Map<String, Object>> response = post("/api/payments/me/" + paymentId + "/card",
                card("4242 4242 4242 4242"), client);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("PAID");
        assertThat(response.getBody().get("method")).isEqualTo("CARD");
        assertThat(response.getBody().get("cardLast4")).isEqualTo("4242");
        assertThat((String) response.getBody().get("providerReference")).startsWith("test_card_");
        assertThat(count("SELECT COUNT(*) FROM email_messages WHERE client_id = ? AND type = 'PAYMENT_RECEIPT'", clientId))
                .isEqualTo(1);

        ResponseEntity<Map<String, Object>> again = post("/api/payments/me/" + paymentId + "/card",
                card("4242424242424242"), client);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void declinedCard_returns402AndPaymentStaysPendingWithAttemptRecorded() {
        ResponseEntity<Map<String, Object>> response = post("/api/payments/me/" + paymentId + "/card",
                card("4000000000000002"), client);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, failed_attempts, last_failure_reason FROM payments WHERE id = ?", paymentId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("failed_attempts")).intValue()).isEqualTo(1);
        assertThat((String) row.get("last_failure_reason")).contains("rechazada");
    }

    @Test
    void invalidCardNumber_isRejected() {
        ResponseEntity<Map<String, Object>> response = post("/api/payments/me/" + paymentId + "/card",
                card("4242424242424241"), client);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody().get("message")).isEqualTo("El número de tarjeta no es válido");
    }

    @Test
    void bizum_approvedAndRejected() {
        ResponseEntity<Map<String, Object>> rejected = post("/api/payments/me/" + paymentId + "/bizum",
                Map.of("phone", "600 000 000"), client);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);

        ResponseEntity<Map<String, Object>> ok = post("/api/payments/me/" + paymentId + "/bizum",
                Map.of("phone", "+34 612 345 678"), client);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody().get("method")).isEqualTo("BIZUM");
        assertThat(ok.getBody().get("bizumPhone")).isEqualTo("*** *** 678");
    }

    @Test
    void clientCannotPayOrRegisterCashForOthers() {
        createClient(trainer, "otro", "otro@mail.com", "otro12345");
        String other = login("otro", "otro12345");

        assertThat(post("/api/payments/me/" + paymentId + "/card", card("4242424242424242"), other).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/payments/" + paymentId + "/cash", Map.of(), client).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void trainerRegistersCash_thenRefunds() {
        ResponseEntity<Map<String, Object>> cash = post("/api/payments/" + paymentId + "/cash",
                Map.of("notes", "Pagado en mano"), trainer);
        assertThat(cash.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cash.getBody().get("method")).isEqualTo("CASH");
        assertThat(cash.getBody().get("status")).isEqualTo("PAID");

        ResponseEntity<Map<String, Object>> summary = get("/api/payments/summary", trainer);
        assertThat(((Number) summary.getBody().get("paidThisMonthAmount")).doubleValue()).isEqualTo(143.0);

        ResponseEntity<Map<String, Object>> refund = post("/api/payments/" + paymentId + "/refund", Map.of(), trainer);
        assertThat(refund.getBody().get("status")).isEqualTo("REFUNDED");
    }

    @Test
    void trainerCreatesAndCancelsManualCharge() {
        ResponseEntity<Map<String, Object>> created = post("/api/payments",
                Map.of("clientId", clientId, "amount", 25.5, "concept", "Sesión presencial extra"), trainer);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long id = id(created);
        assertThat(post("/api/payments/" + id + "/cancel", Map.of(), trainer).getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(getList("/api/payments?status=PENDING", trainer).getBody()).hasSize(1);
        assertThat(getList("/api/clients/" + clientId + "/payments", trainer).getBody()).hasSize(2);
    }
}
