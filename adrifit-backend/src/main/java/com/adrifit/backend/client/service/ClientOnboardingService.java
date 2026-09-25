package com.adrifit.backend.client.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.dto.ClientCreatedResponse;
import com.adrifit.backend.client.dto.CreateClientRequest;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.service.EmailService;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import com.adrifit.backend.subscription.service.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating a client touches several modules (user + profile, subscription, first charge, welcome
 * email). This service orchestrates them in a single transaction.
 */
@Service
public class ClientOnboardingService {

    private final ClientService clientService;
    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;

    public ClientOnboardingService(ClientService clientService,
                                   SubscriptionService subscriptionService,
                                   EmailService emailService,
                                   EmailTemplates emailTemplates) {
        this.clientService = clientService;
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
    }

    @Transactional
    public ClientCreatedResponse create(CreateClientRequest request) {
        ClientCreatedResponse created = clientService.create(request);
        SubscriptionResponse subscription =
                subscriptionService.assignPlan(created.client().id(), new AssignPlanRequest(request.planId()));
        emailService.sendToClient(created.client().id(), EmailType.WELCOME, "Bienvenido/a a AdriFit",
                emailTemplates.welcome(created.client().firstName(), created.username(),
                        created.temporaryPassword(), subscription.planName()));
        return created;
    }

    @Transactional
    public String resetPassword(Long clientId) {
        String temporaryPassword = clientService.resetPassword(clientId);
        Client client = clientService.getEntityById(clientId);
        String username = clientService.findById(clientId).username();
        emailService.sendToClient(clientId, EmailType.PASSWORD_RESET, "Nueva contraseña de acceso",
                emailTemplates.passwordReset(client.getFirstName(), username, temporaryPassword));
        return temporaryPassword;
    }
}
