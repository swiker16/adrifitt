package com.adrifit.backend.payment.gateway;

import java.math.BigDecimal;

/**
 * Payment provider abstraction. The platform ships with {@link TestPaymentGateway}
 * (PAYMENTS_MODE=test); a real provider (Stripe, Redsys...) only needs to implement this interface
 * and be activated with its own mode value.
 */
public interface PaymentGateway {

    GatewayResult chargeCard(CardDetails card, BigDecimal amount, String currency, String description);

    GatewayResult chargeBizum(String phone, BigDecimal amount, String currency, String description);

    GatewayResult refund(String providerReference, BigDecimal amount, String currency);

    String mode();

    record CardDetails(String number, int expMonth, int expYear, String cvc, String holderName) {
    }

    record GatewayResult(boolean success, String reference, String failureReason, String cardBrand, String cardLast4) {

        public static GatewayResult ok(String reference, String brand, String last4) {
            return new GatewayResult(true, reference, null, brand, last4);
        }

        public static GatewayResult failed(String reason, String brand, String last4) {
            return new GatewayResult(false, null, reason, brand, last4);
        }
    }
}
