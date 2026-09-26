package com.adrifit.backend.lead.service;

import com.adrifit.backend.auth.service.AccountActivationService;
import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.dto.ClientCreatedResponse;
import com.adrifit.backend.client.dto.CreateClientRequest;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.client.service.ClientOnboardingService;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.common.security.SecureTokens;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.service.EmailService;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.lead.domain.Lead;
import com.adrifit.backend.lead.domain.LeadStatus;
import com.adrifit.backend.lead.dto.LeadDtos.ApproveRequest;
import com.adrifit.backend.lead.dto.LeadDtos.ContactRequest;
import com.adrifit.backend.lead.dto.LeadDtos.LeadCounts;
import com.adrifit.backend.lead.dto.LeadDtos.LeadDetail;
import com.adrifit.backend.lead.dto.LeadDtos.LeadSummary;
import com.adrifit.backend.lead.dto.LeadDtos.QuestionnaireInfo;
import com.adrifit.backend.lead.dto.Questionnaire;
import com.adrifit.backend.lead.repository.LeadRepository;
import com.adrifit.backend.notification.event.NotificationEvents.LeadReceived;
import com.adrifit.backend.notification.event.NotificationEvents.QuestionnaireCompleted;
import com.adrifit.backend.plan.domain.BillingPeriod;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.plan.repository.PlanRepository;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * New client intake: public contact form → the trainer sends a questionnaire (or declines) →
 * the prospect answers → the trainer accepts (account + activation email) or declines.
 */
@Service
@Transactional(readOnly = true)
public class LeadService {

    static final Duration QUESTIONNAIRE_VALIDITY = Duration.ofDays(14);
    private static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    /** Contact form submissions allowed per IP and hour (spam protection). */
    private static final int MAX_PER_HOUR = 10;
    private static final EnumSet<LeadStatus> OPEN =
            EnumSet.of(LeadStatus.NEW, LeadStatus.QUESTIONNAIRE_SENT, LeadStatus.QUESTIONNAIRE_COMPLETED);

    private final LeadRepository repository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ClientOnboardingService onboarding;
    private final AccountActivationService activation;
    private final EmailService emailService;
    private final EmailTemplates templates;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;
    private final String notifyEmail;
    private final Map<String, Deque<Instant>> submissionsByIp = new ConcurrentHashMap<>();

    public LeadService(LeadRepository repository, PlanRepository planRepository, UserRepository userRepository,
                       ClientRepository clientRepository, SubscriptionRepository subscriptionRepository,
                       ClientOnboardingService onboarding, AccountActivationService activation,
                       EmailService emailService, EmailTemplates templates, ApplicationEventPublisher events,
                       ObjectMapper objectMapper, @Value("${adrifit.leads.notify-email:}") String notifyEmail) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.onboarding = onboarding;
        this.activation = activation;
        this.emailService = emailService;
        this.templates = templates;
        this.events = events;
        this.objectMapper = objectMapper;
        this.notifyEmail = notifyEmail;
    }

    // ── Public: contact form ────────────────────────────────────────────────

    /**
     * Always answers the same way (no hint whether the email exists). Bots filling the honeypot
     * are silently ignored; an email that already has an open request is not duplicated.
     */
    @Transactional
    public void submitContact(ContactRequest request, String ip) {
        if (request.website() != null && !request.website().isBlank()) {
            return;
        }
        rateLimit(ip);
        String email = request.email().trim().toLowerCase();
        String firstName = request.firstName().trim();

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null && existing.getRole() == Role.CLIENT) {
            emailService.queue(null, email, firstName, EmailType.LEAD_RECEIVED, "Ya tienes cuenta en AdriFitt",
                    templates.alreadyClient(firstName));
            return;
        }
        if (repository.existsByEmailIgnoreCaseAndStatusIn(email, OPEN)) {
            return;
        }
        Long planId = request.planId() != null && planRepository.existsById(request.planId()) ? request.planId() : null;
        Lead lead = repository.save(Lead.builder()
                .firstName(firstName)
                .lastName(request.lastName().trim())
                .email(email)
                .phone(blankToNull(request.phone()))
                .objective(request.objective().trim())
                .preferredPlanId(planId)
                .status(LeadStatus.NEW)
                .consentAt(Instant.now())
                .build());

        emailService.queue(null, email, lead.fullName(), EmailType.LEAD_RECEIVED, "Hemos recibido tu solicitud",
                templates.leadReceived(firstName));
        notifyTrainers("Nueva solicitud de " + lead.fullName(), templates.leadNotification(lead.fullName(), email,
                lead.getPhone(), lead.getObjective(), planName(planId)));
        events.publishEvent(new LeadReceived(lead.getId(), lead.fullName(), lead.getObjective()));
    }

    // ── Public: questionnaire ───────────────────────────────────────────────

    public QuestionnaireInfo questionnaireInfo(String token) {
        Lead lead = byToken(token);
        if (lead == null) {
            return new QuestionnaireInfo(null, "CLOSED", null, null);
        }
        String state = switch (lead.getStatus()) {
            case QUESTIONNAIRE_SENT -> isExpired(lead) ? "EXPIRED" : "OPEN";
            case QUESTIONNAIRE_COMPLETED -> "COMPLETED";
            default -> "CLOSED";
        };
        return new QuestionnaireInfo(lead.getFirstName(), state, lead.getQuestionnaireExpiresAt(), lead.getPreferredPlanId());
    }

    @Transactional
    public void submitQuestionnaire(String token, Questionnaire answers) {
        Lead lead = byToken(token);
        if (lead == null || lead.getStatus() != LeadStatus.QUESTIONNAIRE_SENT) {
            throw new ResourceNotFoundException("Este cuestionario ya no está disponible");
        }
        if (isExpired(lead)) {
            throw new BusinessException("El enlace ha caducado. Escríbenos para que te enviemos uno nuevo.");
        }
        if (answers.hasInjuries() && isBlank(answers.injuries())) {
            throw new BusinessException("Describe brevemente tus lesiones o molestias");
        }
        if (answers.hasMedicalConditions() && isBlank(answers.medicalConditions())) {
            throw new BusinessException("Describe brevemente tu condición médica");
        }
        if (answers.planId() != null && !planRepository.existsById(answers.planId())) {
            throw new BusinessException("El plan elegido no existe");
        }
        lead.setQuestionnaireJson(write(answers));
        lead.setQuestionnaireCompletedAt(Instant.now());
        lead.setHealthConsentAt(Instant.now());
        lead.setStatus(LeadStatus.QUESTIONNAIRE_COMPLETED);
        // The link stays readable ("ya lo has enviado") until the decision, but accepts no more answers.
        repository.save(lead);

        boolean flag = healthFlag(answers);
        emailService.queue(null, lead.getEmail(), lead.fullName(), EmailType.LEAD_RECEIVED, "Cuestionario recibido",
                templates.questionnaireReceived(lead.getFirstName()));
        notifyTrainers(lead.fullName() + " ha completado el cuestionario",
                templates.questionnaireCompletedNotification(lead.fullName(), flag));
        events.publishEvent(new QuestionnaireCompleted(lead.getId(), lead.fullName(), flag));
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    public List<LeadSummary> findAll(LeadStatus status) {
        List<Lead> leads = status == null ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByStatusOrderByCreatedAtDesc(status);
        return leads.stream().map(this::toSummary).toList();
    }

    public LeadDetail findById(Long id) {
        return toDetail(getOrThrow(id));
    }

    public LeadCounts counts() {
        return new LeadCounts(repository.countByStatus(LeadStatus.NEW), repository.countByStatus(LeadStatus.QUESTIONNAIRE_SENT),
                repository.countByStatus(LeadStatus.QUESTIONNAIRE_COMPLETED), repository.countByStatus(LeadStatus.ACCEPTED),
                repository.countByStatus(LeadStatus.REJECTED));
    }

    /** Requests that need the trainer: new ones and answered questionnaires. */
    public long countPending() {
        return repository.countByStatus(LeadStatus.NEW) + repository.countByStatus(LeadStatus.QUESTIONNAIRE_COMPLETED);
    }

    /** First filter passed: email the questionnaire link (also used to resend a new link). */
    @Transactional
    public LeadDetail sendQuestionnaire(Long id, String message) {
        Lead lead = getOrThrow(id);
        if (lead.getStatus() != LeadStatus.NEW && lead.getStatus() != LeadStatus.QUESTIONNAIRE_SENT) {
            throw new BusinessException("Solo se envía el cuestionario a solicitudes nuevas o pendientes de responder");
        }
        String token = SecureTokens.generate();
        Instant expires = Instant.now().plus(QUESTIONNAIRE_VALIDITY);
        lead.setQuestionnaireTokenHash(SecureTokens.hash(token));
        lead.setQuestionnaireSentAt(Instant.now());
        lead.setQuestionnaireExpiresAt(expires);
        lead.setStatus(LeadStatus.QUESTIONNAIRE_SENT);
        lead.setDecisionMessage(blankToNull(message));
        repository.save(lead);
        emailService.queue(null, lead.getEmail(), lead.fullName(), EmailType.QUESTIONNAIRE_INVITE,
                "Cuéntanos más sobre ti · AdriFitt",
                templates.questionnaireInvite(lead.getFirstName(), templates.link("/cuestionario/" + token),
                        LocalDate.ofInstant(expires, ZONE), message));
        return toDetail(lead);
    }

    @Transactional
    public LeadDetail reject(Long id, String message) {
        Lead lead = getOrThrow(id);
        if (!OPEN.contains(lead.getStatus())) {
            throw new BusinessException("Esta solicitud ya está cerrada");
        }
        lead.setStatus(LeadStatus.REJECTED);
        lead.setQuestionnaireTokenHash(null);
        lead.setDecisionMessage(blankToNull(message));
        lead.setDecidedAt(Instant.now());
        repository.save(lead);
        emailService.queue(null, lead.getEmail(), lead.fullName(), EmailType.LEAD_REJECTED, "Sobre tu solicitud en AdriFitt",
                templates.leadRejected(lead.getFirstName(), message));
        return toDetail(lead);
    }

    /** Accept after reading the questionnaire: creates the client and emails the activation link. */
    @Transactional
    public LeadDetail approve(Long id, ApproveRequest request) {
        Lead lead = getOrThrow(id);
        if (lead.getStatus() != LeadStatus.QUESTIONNAIRE_COMPLETED) {
            throw new BusinessException("Primero el cliente debe completar el cuestionario");
        }
        if (userRepository.existsByEmail(lead.getEmail())) {
            throw new BusinessException("Ya existe una cuenta con el email " + lead.getEmail());
        }
        Questionnaire q = read(lead.getQuestionnaireJson());
        BillingPeriod period = request.billingPeriod() != null ? request.billingPeriod()
                : q != null && q.billingPeriod() != null ? q.billingPeriod() : BillingPeriod.MONTHLY;
        ClientCreatedResponse created = onboarding.createFromLead(new CreateClientRequest(
                lead.getFirstName(), lead.getLastName(),
                lead.getPhone() != null ? lead.getPhone() : "-",
                q != null ? q.birthDate() : LocalDate.of(1990, 1, 1),
                objectiveOf(lead, q), lead.getEmail(), request.planId(), null, period,
                request.customPrice(), blankToNull(request.customPriceNote()), clientNotes(q)), request.message());

        lead.setStatus(LeadStatus.ACCEPTED);
        lead.setQuestionnaireTokenHash(null);
        lead.setClientId(created.client().id());
        lead.setDecisionMessage(blankToNull(request.message()));
        lead.setDecidedAt(Instant.now());
        repository.save(lead);
        return toDetail(lead);
    }

    @Transactional
    public void resendActivation(Long id) {
        Lead lead = getOrThrow(id);
        if (lead.getStatus() != LeadStatus.ACCEPTED || lead.getClientId() == null) {
            throw new BusinessException("Esta solicitud no tiene una cuenta pendiente de activar");
        }
        Client client = clientRepository.findById(lead.getClientId())
                .orElseThrow(() -> new BusinessException("El cliente ya no existe"));
        if (!activation.isPending(client.getUserId())) {
            throw new BusinessException("La cuenta ya está activada");
        }
        User user = userRepository.findById(client.getUserId()).orElseThrow();
        String plan = subscriptionRepository.findByClientIdAndActiveTrue(client.getId())
                .map(s -> s.getPlan().getName()).orElse(null);
        onboarding.sendActivation(client.getId(), user.getUsername(), plan, lead.getDecisionMessage());
    }

    @Transactional
    public LeadDetail updateNote(Long id, String note) {
        Lead lead = getOrThrow(id);
        lead.setTrainerNote(blankToNull(note));
        return toDetail(repository.save(lead));
    }

    /** GDPR: remove the request and its health answers. */
    @Transactional
    public void delete(Long id) {
        repository.delete(getOrThrow(id));
    }

    /** Initial questionnaire of a client (shown in the client file). */
    public Questionnaire questionnaireOfClient(Long clientId) {
        return repository.findFirstByClientIdOrderByCreatedAtDesc(clientId)
                .map(l -> read(l.getQuestionnaireJson()))
                .orElse(null);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void rateLimit(String ip) {
        String key = ip == null ? "?" : ip;
        Instant cutoff = Instant.now().minus(Duration.ofHours(1));
        Deque<Instant> times = submissionsByIp.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= MAX_PER_HOUR) {
                throw new BusinessException("Has enviado demasiadas solicitudes. Inténtalo más tarde.");
            }
            times.addLast(Instant.now());
        }
    }

    private void notifyTrainers(String subject, String html) {
        if (!isBlank(notifyEmail)) {
            emailService.queue(null, notifyEmail.trim(), "AdriFitt", EmailType.LEAD_NOTIFICATION, subject, html);
            return;
        }
        userRepository.findByRole(Role.TRAINER).forEach(t ->
                emailService.queue(null, t.getEmail(), t.getUsername(), EmailType.LEAD_NOTIFICATION, subject, html));
    }

    private Lead byToken(String token) {
        if (isBlank(token)) {
            return null;
        }
        return repository.findByQuestionnaireTokenHash(SecureTokens.hash(token)).orElse(null);
    }

    private static boolean isExpired(Lead lead) {
        return lead.getQuestionnaireExpiresAt() != null && lead.getQuestionnaireExpiresAt().isBefore(Instant.now());
    }

    private Lead getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + id));
    }

    private String planName(Long planId) {
        return planId == null ? null : planRepository.findById(planId).map(Plan::getName).orElse(null);
    }

    private static boolean healthFlag(Questionnaire q) {
        return q != null && (q.hasInjuries() || q.hasMedicalConditions());
    }

    private static String objectiveOf(Lead lead, Questionnaire q) {
        String goal = q == null ? null : switch (q.mainGoal()) {
            case "PERDER_GRASA" -> "Perder grasa";
            case "GANAR_MUSCULO" -> "Ganar músculo";
            case "RECOMPOSICION" -> "Recomposición corporal";
            case "RENDIMIENTO" -> "Mejorar el rendimiento";
            case "SALUD" -> "Salud y bienestar";
            default -> null;
        };
        String text = goal != null ? goal : lead.getObjective();
        return text.length() > 255 ? text.substring(0, 252) + "…" : text;
    }

    /** Key points of the questionnaire copied to the client's private notes. */
    private static String clientNotes(Questionnaire q) {
        if (q == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder("Alta desde solicitud web (ver cuestionario inicial).");
        if (q.hasInjuries()) sb.append("\nLesiones: ").append(q.injuries());
        if (q.hasMedicalConditions()) sb.append("\nCondiciones médicas: ").append(q.medicalConditions());
        if (!isBlank(q.medication())) sb.append("\nMedicación: ").append(q.medication());
        if (!isBlank(q.allergies())) sb.append("\nAlergias/intolerancias: ").append(q.allergies());
        String notes = sb.toString();
        return notes.length() > 2000 ? notes.substring(0, 2000) : notes;
    }

    private LeadSummary toSummary(Lead l) {
        Questionnaire q = read(l.getQuestionnaireJson());
        return new LeadSummary(l.getId(), l.getFirstName(), l.getLastName(), l.getEmail(), l.getPhone(), l.getObjective(),
                l.getPreferredPlanId(), planName(l.getPreferredPlanId()), l.getStatus(), l.getCreatedAt(),
                l.getQuestionnaireSentAt(), l.getQuestionnaireCompletedAt(), l.getDecidedAt(), l.getClientId(), healthFlag(q));
    }

    private LeadDetail toDetail(Lead l) {
        Questionnaire q = read(l.getQuestionnaireJson());
        boolean pending = false;
        if (l.getStatus() == LeadStatus.ACCEPTED && l.getClientId() != null) {
            pending = clientRepository.findById(l.getClientId()).map(c -> activation.isPending(c.getUserId())).orElse(false);
        }
        return new LeadDetail(toSummary(l), q, q != null ? planName(q.planId()) : null,
                l.getStatus() == LeadStatus.QUESTIONNAIRE_SENT && isExpired(l), l.getQuestionnaireExpiresAt(),
                l.getTrainerNote(), l.getDecisionMessage(), pending);
    }

    private String write(Questionnaire q) {
        try {
            return objectMapper.writeValueAsString(q);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Questionnaire read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Questionnaire.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
