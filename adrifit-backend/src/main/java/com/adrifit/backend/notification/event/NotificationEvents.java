package com.adrifit.backend.notification.event;

import com.adrifit.backend.user.domain.Role;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Domain events that produce a push notification. Published by their owner modules inside the
 * business transaction; notifications are sent only after it commits.
 */
public final class NotificationEvents {

    private NotificationEvents() {
    }

    /** A chat message was sent in the conversation of {@code clientId}. */
    public record MessageSent(Long clientId, Role senderRole, String preview) {
    }

    /** A new charge is waiting to be paid by the client. */
    public record PaymentDue(Long clientId, BigDecimal amount, String concept, LocalDate dueDate) {
    }

    /** Reminder for an unpaid charge that is already past its due date. */
    public record PaymentOverdue(Long clientId, BigDecimal amount, long daysOverdue) {
    }

    /** The client paid online (card/Bizum). */
    public record PaymentReceived(Long clientId, BigDecimal amount, String methodLabel) {
    }

    /** The client sent its check-in. */
    public record ReportSubmitted(Long clientId, Long reportId) {
    }

    /** The client's periodic review is due (daily job). */
    public record ReviewDue(Long clientId, LocalDate reviewDate) {
    }

    /** A technique video was uploaded: by the client (to review) or by the trainer (demonstration). */
    public record VideoUploaded(Long clientId, boolean byTrainer, String exerciseName) {
    }

    /** The trainer corrected the client's technique video. */
    public record VideoReviewed(Long clientId, String exerciseName) {
    }

    /** Someone filled in the public contact form. */
    public record LeadReceived(Long leadId, String name, String objectivePreview) {
    }

    /** A prospect answered the questionnaire. */
    public record QuestionnaireCompleted(Long leadId, String name, boolean healthFlag) {
    }
}
