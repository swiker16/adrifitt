package com.adrifit.backend.payment.gateway;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Simulated gateway: validates the data like a real one would, but never moves money.
 *
 * <p>Test cards (any future expiry date and any 3-digit CVC):
 * <ul>
 *   <li>4242 4242 4242 4242 → approved (also any other valid card number)</li>
 *   <li>4000 0000 0000 0002 → declined</li>
 *   <li>4000 0000 0000 9995 → insufficient funds</li>
 * </ul>
 * Bizum: any Spanish mobile (6xx/7xx) is approved except 600 000 000, which is rejected.
 */
@Component
@ConditionalOnProperty(name = "adrifit.payments.mode", havingValue = "test", matchIfMissing = true)
public class TestPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(TestPaymentGateway.class);

    public static final String DECLINED_CARD = "4000000000000002";
    public static final String NO_FUNDS_CARD = "4000000000009995";
    public static final String REJECTED_BIZUM_PHONE = "600000000";

    @Override
    public GatewayResult chargeCard(CardDetails card, BigDecimal amount, String currency, String description) {
        String number = card.number() == null ? "" : card.number().replaceAll("[\\s-]", "");
        String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : null;
        String brand = brandOf(number);

        if (!number.matches("\\d{13,19}") || !luhn(number)) {
            return GatewayResult.failed("El número de tarjeta no es válido", brand, last4);
        }
        if (card.expMonth() < 1 || card.expMonth() > 12) {
            return GatewayResult.failed("La fecha de caducidad no es válida", brand, last4);
        }
        int year = card.expYear() < 100 ? 2000 + card.expYear() : card.expYear();
        if (YearMonth.of(year, card.expMonth()).isBefore(YearMonth.now())) {
            return GatewayResult.failed("La tarjeta está caducada", brand, last4);
        }
        if (card.cvc() == null || !card.cvc().matches("\\d{3,4}")) {
            return GatewayResult.failed("El CVC no es válido", brand, last4);
        }
        if (DECLINED_CARD.equals(number)) {
            return GatewayResult.failed("Tarjeta rechazada por el banco emisor", brand, last4);
        }
        if (NO_FUNDS_CARD.equals(number)) {
            return GatewayResult.failed("Fondos insuficientes", brand, last4);
        }
        String reference = "test_card_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("[PAYMENTS TEST MODE] card charge {} {} approved ref={}", amount, currency, reference);
        return GatewayResult.ok(reference, brand, last4);
    }

    @Override
    public GatewayResult chargeBizum(String phone, BigDecimal amount, String currency, String description) {
        String normalized = normalizePhone(phone);
        if (!normalized.matches("[67]\\d{8}")) {
            return GatewayResult.failed("El teléfono no es un móvil español válido", null, null);
        }
        if (REJECTED_BIZUM_PHONE.equals(normalized)) {
            return GatewayResult.failed("La operación Bizum ha sido rechazada", null, null);
        }
        String reference = "test_bizum_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("[PAYMENTS TEST MODE] bizum charge {} {} approved ref={}", amount, currency, reference);
        return GatewayResult.ok(reference, null, null);
    }

    @Override
    public GatewayResult refund(String providerReference, BigDecimal amount, String currency) {
        String reference = "test_refund_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("[PAYMENTS TEST MODE] refund of {} ({} {}) ref={}", providerReference, amount, currency, reference);
        return GatewayResult.ok(reference, null, null);
    }

    @Override
    public String mode() {
        return "test";
    }

    public static String normalizePhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("[\\s-]", "");
        if (digits.startsWith("+34")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0034")) {
            digits = digits.substring(4);
        }
        return digits;
    }

    private static String brandOf(String number) {
        if (number.startsWith("4")) return "VISA";
        if (number.matches("5[1-5].*") || number.matches("2[2-7].*")) return "MASTERCARD";
        if (number.matches("3[47].*")) return "AMEX";
        return "CARD";
    }

    private static boolean luhn(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
