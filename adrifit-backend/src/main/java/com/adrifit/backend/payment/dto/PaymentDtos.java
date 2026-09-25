package com.adrifit.backend.payment.dto;

import com.adrifit.backend.payment.domain.PaymentMethod;
import com.adrifit.backend.payment.domain.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Request/response records of the payment API. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record PaymentResponse(
            Long id,
            Long clientId,
            String clientName,
            Long subscriptionId,
            String concept,
            BigDecimal amount,
            String currency,
            PaymentStatus status,
            PaymentMethod method,
            LocalDate dueDate,
            LocalDate periodStart,
            LocalDate periodEnd,
            Instant paidAt,
            Instant refundedAt,
            String providerReference,
            String cardBrand,
            String cardLast4,
            String bizumPhone,
            int failedAttempts,
            String lastFailureReason,
            String trainerNotes,
            boolean overdue,
            Instant createdAt
    ) {
    }

    public record CreatePaymentRequest(
            @NotNull(message = "El cliente es obligatorio") Long clientId,
            @NotNull(message = "El importe es obligatorio")
            @DecimalMin(value = "0.01", message = "El importe debe ser mayor que 0") BigDecimal amount,
            @NotBlank(message = "El concepto es obligatorio") @Size(max = 300) String concept,
            LocalDate dueDate
    ) {
    }

    public record CardPaymentRequest(
            @NotBlank(message = "El número de tarjeta es obligatorio") @Size(max = 30) String cardNumber,
            @NotNull @Min(1) @Max(12) Integer expMonth,
            @NotNull @Min(0) @Max(2100) Integer expYear,
            @NotBlank(message = "El CVC es obligatorio") @Size(max = 4) String cvc,
            @NotBlank(message = "El titular es obligatorio") @Size(max = 100) String holderName
    ) {
    }

    public record BizumPaymentRequest(
            @NotBlank(message = "El teléfono es obligatorio") @Size(max = 20) String phone
    ) {
    }

    public record TrainerNoteRequest(@Size(max = 500) String notes) {
    }

    public record PaymentSummaryResponse(
            long pendingCount,
            BigDecimal pendingAmount,
            long overdueCount,
            BigDecimal overdueAmount,
            long paidThisMonthCount,
            BigDecimal paidThisMonthAmount,
            String gatewayMode
    ) {
    }
}
