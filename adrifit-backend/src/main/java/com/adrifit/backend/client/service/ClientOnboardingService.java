package com.adrifit.backend.client.service;

import com.adrifit.backend.auth.service.AccountActivationService;
import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.dto.ClientCreatedResponse;
import com.adrifit.backend.client.dto.CreateClientRequest;
import com.adrifit.backend.email.domain.EmailType;
import com.adrifit.backend.email.service.EmailService;
import com.adrifit.backend.email.service.EmailTemplates;
import com.adrifit.backend.subscription.dto.AssignPlanRequest;
import com.adrifit.backend.subscription.dto.SubscriptionResponse;
import com.adrifit.backend.subscription.service.SubscriptionService;
import com.adrifit.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final AccountActivationService activationService;
    private final UserRepository userRepository;

    public ClientOnboardingService(ClientService clientService,
                                   SubscriptionService subscriptionService,
                                   EmailService emailService,
                                   EmailTemplates emailTemplates,
                                   AccountActivationService activationService,
                                   UserRepository userRepository) {
        this.clientService = clientService;
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
        this.activationService = activationService;
        this.userRepository = userRepository;
    }

    /**
     * Client accepted through the intake flow (request → questionnaire): same account and
     * subscription as a manual creation, but the email carries an activation link where the
     * person chooses their own password instead of a temporary one.
     */
    @Transactional
    public ClientCreatedResponse createFromLead(CreateClientRequest request, String personalMessage) {
        ClientCreatedResponse created = clientService.create(request);
        SubscriptionResponse subscription =
                subscriptionService.assignPlan(created.client().id(), new AssignPlanRequest(request.planId(),
                        request.billingPeriod(), request.customPrice(), request.customPriceNote()));
        sendActivation(created.client().id(), created.username(), subscription.planName(), personalMessage);
        return created;
    }

    /** (Re)sends the activation link of a client that has not activated the account yet. */
    @Transactional
    public void sendActivation(Long clientId, String username, String planName, String personalMessage) {
        Client client = clientService.getEntityById(clientId);
        var user = userRepository.findById(client.getUserId()).orElseThrow();
        String token = activationService.issue(user);
        LocalDate expiresOn = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(AccountActivationService.VALIDITY.toDays());
        emailService.sendToClient(clientId, EmailType.ACCOUNT_ACTIVATION, "Activa tu cuenta de AdriFitt",
                emailTemplates.accountActivation(client.getFirstName(), username, planName,
                        emailTemplates.link("/activar/" + token), expiresOn, personalMessage));
    }

    @Transactional
    public ClientCreatedResponse create(CreateClientRequest request) {
        ClientCreatedResponse created = clientService.create(request);
        SubscriptionResponse subscription =
                subscriptionService.assignPlan(created.client().id(), new AssignPlanRequest(request.planId(),
                        request.billingPeriod(), request.customPrice(), request.customPriceNote()));
        emailService.sendToClient(created.client().id(), EmailType.WELCOME, "Bienvenido/a a AdriFitt",
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
