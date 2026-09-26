package com.adrifit.backend.notification.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.notification.dto.PushDtos.PushMessage;
import com.adrifit.backend.notification.event.NotificationEvents.MessageSent;
import com.adrifit.backend.notification.event.NotificationEvents.PaymentDue;
import com.adrifit.backend.notification.event.NotificationEvents.PaymentOverdue;
import com.adrifit.backend.notification.event.NotificationEvents.PaymentReceived;
import com.adrifit.backend.notification.event.NotificationEvents.ReportSubmitted;
import com.adrifit.backend.notification.event.NotificationEvents.ReviewDue;
import com.adrifit.backend.report.event.ReportReviewedEvent;
import com.adrifit.backend.user.domain.Role;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns domain events into push notifications for the right people. Runs after the business
 * transaction commits and asynchronously, so a slow push service never delays the user.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");

    private final PushNotificationService push;
    private final ClientRepository clientRepository;

    public NotificationDispatcher(PushNotificationService push, ClientRepository clientRepository) {
        this.push = push;
        this.clientRepository = clientRepository;
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onMessage(MessageSent e) {
        safely(() -> {
            if (e.senderRole() == Role.TRAINER) {
                push.sendToClient(e.clientId(), new PushMessage("💬 Mensaje de tu entrenador", preview(e.preview()),
                        "/client/messages", "chat"));
            } else {
                push.sendToTrainers(new PushMessage("💬 " + name(e.clientId()), preview(e.preview()),
                        "/trainer/messages?clientId=" + e.clientId(), "chat-" + e.clientId()));
            }
        });
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPaymentDue(PaymentDue e) {
        safely(() -> push.sendToClient(e.clientId(), new PushMessage("💳 Nuevo pago pendiente",
                EmailTemplates.money(e.amount()) + " · " + e.concept() + ". Puedes pagar con tarjeta o Bizum.",
                "/client/subscription", "payment")));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPaymentOverdue(PaymentOverdue e) {
        safely(() -> push.sendToClient(e.clientId(), new PushMessage("⏰ Tienes un pago vencido",
                EmailTemplates.money(e.amount()) + " pendiente desde hace " + e.daysOverdue() + " días.",
                "/client/subscription", "payment")));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onPaymentReceived(PaymentReceived e) {
        safely(() -> push.sendToTrainers(new PushMessage("✅ Pago recibido",
                name(e.clientId()) + " ha pagado " + EmailTemplates.money(e.amount()) + " con " + e.methodLabel() + ".",
                "/trainer/payments?clientId=" + e.clientId(), "payment-" + e.clientId())));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReportSubmitted(ReportSubmitted e) {
        safely(() -> push.sendToTrainers(new PushMessage("📸 Nuevo seguimiento",
                name(e.clientId()) + " ha enviado su seguimiento. ¡Toca revisarlo!",
                "/trainer/reports", "report-" + e.clientId())));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReportReviewed(ReportReviewedEvent e) {
        safely(() -> push.sendToClient(e.clientId(), new PushMessage("📝 Tu entrenador ha revisado tu seguimiento",
                "Ya tienes su feedback. ¡Échale un vistazo!", "/client/report", "feedback")));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onReviewDue(ReviewDue e) {
        safely(() -> {
            String when = e.reviewDate().isAfter(LocalDate.now()) ? "mañana (" + e.reviewDate().format(DATE) + ")" : "hoy";
            push.sendToClient(e.clientId(), new PushMessage("📅 Toca revisión",
                    "Tu revisión es " + when + ". Envía tus fotos y tu peso para que tu entrenador ajuste el plan.",
                    "/client/report", "review"));
        });
    }

    private String name(Long clientId) {
        return clientRepository.findById(clientId)
                .map(Client::getFirstName)
                .orElse("Un cliente");
    }

    private static String preview(String text) {
        if (text == null) return "";
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 140 ? oneLine.substring(0, 137) + "…" : oneLine;
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Push notification failed: {}", ex.getMessage());
        }
    }
}
