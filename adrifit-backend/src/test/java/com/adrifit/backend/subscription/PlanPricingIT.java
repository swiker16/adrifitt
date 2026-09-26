package com.adrifit.backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import com.adrifit.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PlanPricingIT extends AbstractIntegrationTest {

    private String trainer;

    @BeforeEach
    void setUp() {
        trainer = trainerToken();
    }

    @Test
    void catalogueHasBasicaAndPremiumWithEveryBillingPeriod() {
        List<Map<String, Object>> plans = getList("/api/plans?activeOnly=true", null).getBody();

        assertThat(plans).extracting(p -> p.get("name")).containsExactly("Básica", "Premium");
        Map<String, Object> premium = plans.get(1);
        assertThat(((Number) premium.get("monthlyPrice")).doubleValue()).isEqualTo(143.0);
        assertThat(((Number) premium.get("annualPrice")).doubleValue()).isEqualTo(1573.0);
        assertThat(premium.get("reviewFrequencyDays")).isEqualTo(7);
        assertThat((List<?>) premium.get("features")).hasSize(8);

        List<Map<String, Object>> prices = (List<Map<String, Object>>) plans.get(0).get("prices");
        assertThat(prices).extracting(p -> p.get("period")).containsExactly("MONTHLY", "QUARTERLY", "SEMIANNUAL", "ANNUAL");
        Map<String, Object> annual = prices.get(3);
        assertThat(((Number) annual.get("price")).doubleValue()).isEqualTo(1295.0);
        assertThat(((Number) annual.get("monthlyEquivalent")).doubleValue()).isEqualTo(107.92);
        assertThat(annual.get("savingPercent")).isEqualTo(8);
    }

    @Test
    void specialConditions_premiumAt85_chargesAndMrrUseTheSpecialPrice() {
        Map<String, Object> body = clientBody("iker@mail.com", "Premium");
        body.put("customPrice", 85);
        body.put("customPriceNote", "Condiciones especiales");
        ResponseEntity<Map<String, Object>> created = post("/api/clients", body, trainer);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long clientId = clientIdOf(created);

        Map<String, Object> sub = get("/api/clients/" + clientId + "/subscription", trainer).getBody();
        assertThat(sub.get("planName")).isEqualTo("Premium");
        assertThat(((Number) sub.get("customPrice")).doubleValue()).isEqualTo(85.0);
        assertThat(((Number) sub.get("effectivePrice")).doubleValue()).isEqualTo(85.0);
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("85");
        assertThat(((Number) get("/api/dashboard/trainer", trainer).getBody().get("monthlyRecurringRevenue")).doubleValue())
                .isEqualTo(85.0);

        // Renewals keep the special price.
        jdbc.update("UPDATE subscriptions SET renewal_date = ? WHERE client_id = ?", LocalDate.now(), clientId);
        jdbc.update("UPDATE payments SET status = 'CANCELLED' WHERE client_id = ?", clientId);
        post("/api/jobs/daily/run", Map.of(), trainer);
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("85");
    }

    @Test
    void trainerChangesSpecialPrice_unpaidChargeFollows_andCanRemoveIt() {
        Long clientId = clientIdOf(post("/api/clients", clientBody("ana@mail.com", "Premium"), trainer));
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("143");

        ResponseEntity<Map<String, Object>> updated = patch("/api/clients/" + clientId + "/subscription/pricing",
                Map.of("customPrice", 99, "customPriceNote", "Amiga"), trainer);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("99");

        Map<String, Object> reset = new HashMap<>();
        reset.put("customPrice", null);
        assertThat(((Number) patch("/api/clients/" + clientId + "/subscription/pricing", reset, trainer)
                .getBody().get("effectivePrice")).doubleValue()).isEqualTo(143.0);
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("143");
    }

    @Test
    void annualBilling_renewsAfter12MonthsAndChargesTheAnnualPrice() {
        Map<String, Object> body = clientBody("ana@mail.com", "Básica");
        body.put("billingPeriod", "ANNUAL");
        Long clientId = clientIdOf(post("/api/clients", body, trainer));

        Map<String, Object> sub = get("/api/clients/" + clientId + "/subscription", trainer).getBody();
        assertThat(sub.get("billingPeriod")).isEqualTo("ANNUAL");
        assertThat(sub.get("renewalDate")).isEqualTo(LocalDate.now().plusMonths(12).toString());
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("1295");
        assertThat(((Number) sub.get("monthlyEquivalent")).doubleValue()).isEqualTo(107.92);
    }

    @Test
    void clientChangesToQuarterly_andSpecialConditionsAreDroppedOnSelfServiceChange() {
        Map<String, Object> body = clientBody("ana@mail.com", "Premium");
        body.put("customPrice", 85);
        Long clientId = clientIdOf(post("/api/clients", body, trainer));
        jdbc.update("UPDATE users SET username = 'ana', password = ?, must_change_password = FALSE WHERE email = 'ana@mail.com'",
                passwordEncoder.encode("ana12345"));
        String client = login("ana", "ana12345");

        ResponseEntity<Map<String, Object>> changed = post("/api/subscriptions/me/change-plan",
                Map.of("planId", planId("Básica"), "billingPeriod", "QUARTERLY"), client);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changed.getBody().get("customPrice")).isNull();
        assertThat(pendingAmount(clientId)).isEqualByComparingTo("345");
        assertThat(changed.getBody().get("renewalDate")).isEqualTo(LocalDate.now().plusMonths(3).toString());

        assertThat(post("/api/subscriptions/me/change-plan",
                Map.of("planId", planId("Básica"), "billingPeriod", "QUARTERLY"), client).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void periodNotOfferedByThePlan_isRejected() {
        createPlan(trainer, "Solo mensual", true, true);
        Map<String, Object> body = clientBody("ana@mail.com", "Solo mensual");
        body.put("billingPeriod", "ANNUAL");
        assertThat(post("/api/clients", body, trainer).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private Map<String, Object> clientBody(String email, String plan) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Iker");
        body.put("lastName", "Test");
        body.put("phone", "600111222");
        body.put("birthDate", "1995-01-01");
        body.put("objective", "Fuerza");
        body.put("email", email);
        body.put("planId", planId(plan));
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Long clientIdOf(ResponseEntity<Map<String, Object>> created) {
        return ((Number) ((Map<String, Object>) created.getBody().get("client")).get("id")).longValue();
    }

    private BigDecimal pendingAmount(Long clientId) {
        return jdbc.queryForObject("SELECT amount FROM payments WHERE client_id = ? AND status = 'PENDING'",
                BigDecimal.class, clientId);
    }
}
