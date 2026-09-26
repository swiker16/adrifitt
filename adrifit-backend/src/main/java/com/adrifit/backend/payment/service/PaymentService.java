package com.adrifit.backend.payment.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.PaymentFailedException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.service.EmailService;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.payment.domain.Payment;
import com.adrifit.backend.payment.domain.PaymentMethod;
import com.adrifit.backend.payment.domain.PaymentStatus;
import com.adrifit.backend.payment.dto.PaymentDtos.BizumPaymentRequest;
import com.adrifit.backend.payment.dto.PaymentDtos.CardPaymentRequest;
import com.adrifit.backend.payment.dto.PaymentDtos.CreatePaymentRequest;
import com.adrifit.backend.payment.dto.PaymentDtos.PaymentResponse;
import com.adrifit.backend.payment.dto.PaymentDtos.PaymentSummaryResponse;
import com.adrifit.backend.payment.gateway.PaymentGateway;
import com.adrifit.backend.payment.gateway.PaymentGateway.CardDetails;
import com.adrifit.backend.payment.gateway.PaymentGateway.GatewayResult;
import com.adrifit.backend.payment.gateway.TestPaymentGateway;
import com.adrifit.backend.payment.repository.PaymentRepository;
import com.adrifit.backend.subscription.event.SubscriptionChargeEvent;
import com.adrifit.backend.subscription.event.SubscriptionClosedEvent;
import com.adrifit.backend.subscription.event.SubscriptionPriceChangedEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PaymentRepository paymentRepository;
    private final PaymentGateway gateway;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;
    private final String currency;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentGateway gateway,
                          ClientService clientService,
                          ClientRepository clientRepository,
                          EmailService emailService,
                          EmailTemplates emailTemplates,
                          @Value("${adrifit.payments.currency:EUR}") String currency) {
        this.paymentRepository = paymentRepository;
        this.gateway = gateway;
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
        this.currency = currency;
    }

    // ── Subscription events ─────────────────────────────────────────────────

    @EventListener
    @Transactional
    public void onSubscriptionCharge(SubscriptionChargeEvent event) {
        if (event.amount() == null || event.amount().signum() <= 0) {
            return;
        }
        String concept = "Plan " + event.planName() + " · " + event.periodStart().format(DATE)
                + " - " + event.periodEnd().format(DATE);
        Payment payment = paymentRepository.save(Payment.builder()
                .clientId(event.clientId())
                .subscriptionId(event.subscriptionId())
                .concept(concept)
                .amount(event.amount())
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .dueDate(event.periodStart())
                .periodStart(event.periodStart())
                .periodEnd(event.periodEnd())
                .build());
        notifyDue(payment);
    }

    @EventListener
    @Transactional
    public void onSubscriptionClosed(SubscriptionClosedEvent event) {
        for (Payment payment : paymentRepository.findBySubscriptionIdAndStatus(event.subscriptionId(), PaymentStatus.PENDING)) {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepository.save(payment);
        }
    }

    @EventListener
    @Transactional
    public void onSubscriptionPriceChanged(SubscriptionPriceChangedEvent event) {
        if (event.newAmount() == null || event.newAmount().signum() <= 0) {
            return;
        }
        for (Payment payment : paymentRepository.findBySubscriptionIdAndStatus(event.subscriptionId(), PaymentStatus.PENDING)) {
            payment.setAmount(event.newAmount());
            paymentRepository.save(payment);
        }
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        paymentRepository.deleteAll(paymentRepository.findByClientId(event.clientId()));
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    public List<PaymentResponse> findAll(PaymentStatus status, Long clientId) {
        List<Payment> payments;
        if (clientId != null) {
            payments = paymentRepository.findByClientIdOrderByDueDateDescIdDesc(clientId);
            if (status != null) {
                payments = payments.stream().filter(p -> p.getStatus() == status).toList();
            }
        } else if (status != null) {
            payments = paymentRepository.findByStatusOrderByDueDateAscIdAsc(status);
        } else {
            payments = paymentRepository.findAllByOrderByDueDateDescIdDesc();
        }
        return toResponses(payments);
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        Client client = clientService.getEntityById(request.clientId());
        Payment payment = paymentRepository.save(Payment.builder()
                .clientId(client.getId())
                .concept(request.concept().trim())
                .amount(request.amount())
                .currency(currency)
                .status(PaymentStatus.PENDING)
                .dueDate(request.dueDate() != null ? request.dueDate() : LocalDate.now())
                .build());
        notifyDue(payment);
        return toResponse(payment, client);
    }

    /** Cash is handed to the trainer, so only the trainer can register it. */
    @Transactional
    public PaymentResponse markCashPaid(Long id, String notes) {
        Payment payment = getOrThrow(id);
        requireStatus(payment, PaymentStatus.PENDING, "Solo se pueden cobrar pagos pendientes");
        payment.setMethod(PaymentMethod.CASH);
        payment.setTrainerNotes(notes);
        markPaid(payment, null);
        return toResponse(payment);
    }

    @Transactional
    public PaymentResponse cancel(Long id) {
        Payment payment = getOrThrow(id);
        requireStatus(payment, PaymentStatus.PENDING, "Solo se pueden anular pagos pendientes");
        payment.setStatus(PaymentStatus.CANCELLED);
        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public PaymentResponse refund(Long id) {
        Payment payment = getOrThrow(id);
        requireStatus(payment, PaymentStatus.PAID, "Solo se pueden devolver pagos cobrados");
        if (payment.getMethod() != PaymentMethod.CASH) {
            GatewayResult result = gateway.refund(payment.getProviderReference(), payment.getAmount(), payment.getCurrency());
            if (!result.success()) {
                throw new PaymentFailedException("No se pudo realizar la devolución: " + result.failureReason());
            }
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        return toResponse(paymentRepository.save(payment));
    }

    public PaymentSummaryResponse summary() {
        LocalDate today = LocalDate.now();
        List<Payment> pending = paymentRepository.findByStatus(PaymentStatus.PENDING);
        List<Payment> overdue = pending.stream().filter(p -> p.getDueDate().isBefore(today)).toList();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        List<Payment> paidThisMonth = paymentRepository.findByStatusAndPaidAtBetween(PaymentStatus.PAID, monthStart, Instant.now());
        return new PaymentSummaryResponse(
                pending.size(), sum(pending),
                overdue.size(), sum(overdue),
                paidThisMonth.size(), sum(paidThisMonth),
                gateway.mode());
    }

    // ── Client ──────────────────────────────────────────────────────────────

    public List<PaymentResponse> findMine() {
        Client client = clientService.getCurrentClient();
        return paymentRepository.findByClientIdOrderByDueDateDescIdDesc(client.getId()).stream()
                .map(p -> toResponse(p, client)).toList();
    }

    public List<PaymentResponse> findForClient(Long clientId) {
        Client client = clientService.assertCanAccess(clientId);
        return paymentRepository.findByClientIdOrderByDueDateDescIdDesc(clientId).stream()
                .map(p -> toResponse(p, client)).toList();
    }

    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResponse payMineWithCard(Long id, CardPaymentRequest request) {
        Payment payment = getMinePending(id);
        GatewayResult result = gateway.chargeCard(
                new CardDetails(request.cardNumber(), request.expMonth(), request.expYear(), request.cvc(), request.holderName()),
                payment.getAmount(), payment.getCurrency(), payment.getConcept());
        payment.setCardBrand(result.cardBrand());
        payment.setCardLast4(result.cardLast4());
        return settle(payment, PaymentMethod.CARD, result);
    }

    @Transactional(noRollbackFor = PaymentFailedException.class)
    public PaymentResponse payMineWithBizum(Long id, BizumPaymentRequest request) {
        Payment payment = getMinePending(id);
        GatewayResult result = gateway.chargeBizum(request.phone(), payment.getAmount(), payment.getCurrency(), payment.getConcept());
        String phone = TestPaymentGateway.normalizePhone(request.phone());
        payment.setBizumPhone(phone.length() >= 3 ? "*** *** " + phone.substring(phone.length() - 3) : null);
        return settle(payment, PaymentMethod.BIZUM, result);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private PaymentResponse settle(Payment payment, PaymentMethod method, GatewayResult result) {
        payment.setLastAttemptAt(Instant.now());
        if (!result.success()) {
            payment.setFailedAttempts(payment.getFailedAttempts() + 1);
            payment.setLastFailureReason(result.failureReason());
            paymentRepository.save(payment);
            throw new PaymentFailedException(result.failureReason());
        }
        payment.setMethod(method);
        payment.setLastFailureReason(null);
        markPaid(payment, result.reference());
        return toResponse(payment);
    }

    private void markPaid(Payment payment, String reference) {
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setProviderReference(reference);
        paymentRepository.save(payment);
        Client client = clientRepository.findById(payment.getClientId()).orElse(null);
        if (client != null) {
            emailService.sendToClient(client.getId(), EmailType.PAYMENT_RECEIPT, "Recibo de tu pago",
                    emailTemplates.paymentReceipt(client.getFirstName(), payment.getConcept(), payment.getAmount(),
                            methodLabel(payment.getMethod()), reference));
        }
    }

    private void notifyDue(Payment payment) {
        clientRepository.findById(payment.getClientId()).ifPresent(client ->
                emailService.sendToClient(client.getId(), EmailType.PAYMENT_DUE, "Nuevo pago pendiente",
                        emailTemplates.paymentDue(client.getFirstName(), payment.getConcept(), payment.getAmount(),
                                payment.getDueDate())));
    }

    private Payment getMinePending(Long id) {
        Long clientId = clientService.getCurrentClientId();
        Payment payment = getOrThrow(id);
        if (!payment.getClientId().equals(clientId)) {
            // Do not reveal other clients' payments.
            throw new ResourceNotFoundException("Payment not found: " + id);
        }
        requireStatus(payment, PaymentStatus.PENDING, "Este pago ya no está pendiente");
        return payment;
    }

    private void requireStatus(Payment payment, PaymentStatus expected, String message) {
        if (payment.getStatus() != expected) {
            throw new BusinessException(message);
        }
    }

    private Payment getOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    private static BigDecimal sum(Collection<Payment> payments) {
        return payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String methodLabel(PaymentMethod method) {
        if (method == null) return "—";
        return switch (method) {
            case CARD -> "Tarjeta";
            case BIZUM -> "Bizum";
            case CASH -> "Efectivo";
        };
    }

    private List<PaymentResponse> toResponses(List<Payment> payments) {
        Map<Long, Client> clients = clientRepository.findAllById(
                        payments.stream().map(Payment::getClientId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Client::getId, Function.identity()));
        return payments.stream().map(p -> toResponse(p, clients.get(p.getClientId()))).toList();
    }

    private PaymentResponse toResponse(Payment p) {
        return toResponse(p, clientRepository.findById(p.getClientId()).orElse(null));
    }

    private PaymentResponse toResponse(Payment p, Client client) {
        boolean overdue = p.getStatus() == PaymentStatus.PENDING && p.getDueDate().isBefore(LocalDate.now());
        return new PaymentResponse(
                p.getId(), p.getClientId(),
                client != null ? client.getFirstName() + " " + client.getLastName() : null,
                p.getSubscriptionId(), p.getConcept(), p.getAmount(), p.getCurrency(), p.getStatus(), p.getMethod(),
                p.getDueDate(), p.getPeriodStart(), p.getPeriodEnd(), p.getPaidAt(), p.getRefundedAt(),
                p.getProviderReference(), p.getCardBrand(), p.getCardLast4(), p.getBizumPhone(),
                p.getFailedAttempts(), p.getLastFailureReason(), p.getTrainerNotes(), overdue, p.getCreatedAt());
    }
}
