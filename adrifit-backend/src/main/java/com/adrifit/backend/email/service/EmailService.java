package com.adrifit.backend.email.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.common.exception.ResourceNotFoundException;
import com.adrifit.backend.email.domain.EmailMessage;
import com.adrifit.backend.email.domain.EmailStatus;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.dto.EmailResponse;
import com.adrifit.backend.email.dto.SendEmailRequest;
import com.adrifit.backend.email.repository.EmailMessageRepository;
import com.adrifit.backend.subscription.domain.SubscriptionStatus;
import com.adrifit.backend.subscription.repository.SubscriptionRepository;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Every email is first stored in the outbox (email_messages) as part of the business transaction
 * and delivered only after that transaction commits, so a rolled back operation never sends mail.
 */
@Service
@Transactional(readOnly = true)
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailMessageRepository repository;
    private final EmailSender sender;
    private final EmailTemplates templates;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TransactionTemplate requiresNew;
    private final String from;

    public EmailService(EmailMessageRepository repository,
                        EmailSender sender,
                        EmailTemplates templates,
                        ClientRepository clientRepository,
                        UserRepository userRepository,
                        SubscriptionRepository subscriptionRepository,
                        PlatformTransactionManager transactionManager,
                        @Value("${adrifit.mail.from:AdriFitt <no-reply@adrifitt.app>}") String from) {
        this.repository = repository;
        this.sender = sender;
        this.templates = templates;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.from = from;
    }

    /**
     * Queues an email for a client. The html body must be complete (see {@link EmailTemplates}).
     * Silently skipped if the client has no user/email.
     */
    @Transactional
    public void sendToClient(Long clientId, EmailType type, String subject, String htmlBody) {
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null) {
            return;
        }
        User user = userRepository.findById(client.getUserId()).orElse(null);
        if (user == null || user.getEmail() == null) {
            return;
        }
        queue(clientId, user.getEmail(), client.getFirstName() + " " + client.getLastName(), type, subject, htmlBody);
    }

    @Transactional
    public EmailMessage queue(Long clientId, String toAddress, String toName, EmailType type,
                              String subject, String htmlBody) {
        EmailMessage message = repository.save(EmailMessage.builder()
                .clientId(clientId)
                .toAddress(toAddress)
                .toName(toName)
                .type(type)
                .subject(subject)
                .htmlBody(htmlBody)
                .status(EmailStatus.QUEUED)
                .mode(sender.mode())
                .build());

        Long id = message.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(id);
                }
            });
        } else {
            dispatch(id);
        }
        return message;
    }

    private void dispatch(Long emailId) {
        requiresNew.executeWithoutResult(status -> {
            EmailMessage message = repository.findById(emailId).orElse(null);
            if (message == null) {
                return;
            }
            try {
                sender.deliver(from, message.getToAddress(), message.getSubject(), message.getHtmlBody());
                message.setStatus("test".equals(sender.mode()) ? EmailStatus.TEST_CAPTURED : EmailStatus.SENT);
                message.setSentAt(Instant.now());
            } catch (RuntimeException ex) {
                log.warn("Email {} to {} failed: {}", emailId, message.getToAddress(), ex.getMessage());
                message.setStatus(EmailStatus.FAILED);
                String error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                message.setErrorMessage(error.length() > 1000 ? error.substring(0, 1000) : error);
            }
            repository.save(message);
        });
    }

    // ── Trainer API ─────────────────────────────────────────────────────────

    public List<EmailResponse> findAll(Long clientId) {
        List<EmailMessage> messages = clientId != null
                ? repository.findByClientIdOrderByCreatedAtDesc(clientId)
                : repository.findTop200ByOrderByCreatedAtDesc();
        return messages.stream().map(this::toResponse).toList();
    }

    public EmailResponse findById(Long id) {
        return repository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Email not found: " + id));
    }

    @Transactional
    public List<EmailResponse> sendCustom(SendEmailRequest request) {
        Set<Long> targetIds = new LinkedHashSet<>();
        if (request.clientIds() != null) {
            targetIds.addAll(request.clientIds());
        }
        if (request.allActiveClients()) {
            subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)
                    .forEach(s -> targetIds.add(s.getClientId()));
        }
        if (targetIds.isEmpty()) {
            throw new BusinessException("Selecciona al menos un destinatario");
        }

        Map<Long, Client> clients = clientRepository.findAllById(targetIds).stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));
        List<EmailResponse> result = new ArrayList<>();
        for (Long clientId : targetIds) {
            Client client = clients.get(clientId);
            if (client == null) {
                throw new ResourceNotFoundException("Client not found with id: " + clientId);
            }
            User user = userRepository.findById(client.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            EmailMessage message = queue(clientId, user.getEmail(), client.getFirstName() + " " + client.getLastName(),
                    EmailType.CUSTOM, request.subject(), templates.custom(client.getFirstName(), request.body()));
            result.add(toResponse(message));
        }
        return result;
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        repository.detachClient(event.clientId());
    }

    private EmailResponse toResponse(EmailMessage m) {
        return new EmailResponse(m.getId(), m.getClientId(), m.getToAddress(), m.getToName(), m.getSubject(),
                m.getHtmlBody(), m.getType(), m.getStatus(), m.getMode(), m.getErrorMessage(),
                m.getCreatedAt(), m.getSentAt());
    }
}
