package com.adrifit.backend.message.service;

import com.adrifit.backend.client.domain.Client;
import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.client.service.ClientService;
import com.adrifit.backend.common.event.ClientDeletedEvent;
import com.adrifit.backend.message.domain.Message;
import com.adrifit.backend.message.dto.MessageDtos.ConversationResponse;
import com.adrifit.backend.message.dto.MessageDtos.ConversationSummary;
import com.adrifit.backend.message.dto.MessageDtos.MessageResponse;
import com.adrifit.backend.message.dto.MessageDtos.MyConversationResponse;
import com.adrifit.backend.message.repository.MessageRepository;
import com.adrifit.backend.plan.domain.Plan;
import com.adrifit.backend.subscription.service.SubscriptionService;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.service.UserService;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Client ⇄ trainer chat. Sending is only possible while the client's ACTIVE plan has
 * {@code messagingEnabled}. The trainer can always read the history.
 */
@Service
@Transactional(readOnly = true)
public class MessageService {

    private static final String NOT_INCLUDED = "Tu plan actual no incluye mensajería con el entrenador";
    private static final String NOT_INCLUDED_TRAINER = "El plan de este cliente no incluye mensajería";

    private final MessageRepository repository;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final SubscriptionService subscriptionService;
    private final UserService userService;

    public MessageService(MessageRepository repository,
                          ClientService clientService,
                          ClientRepository clientRepository,
                          SubscriptionService subscriptionService,
                          UserService userService) {
        this.repository = repository;
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.subscriptionService = subscriptionService;
        this.userService = userService;
    }

    // ── Client ──────────────────────────────────────────────────────────────

    public MyConversationResponse getMine() {
        Client client = clientService.getCurrentClient();
        Optional<Plan> plan = subscriptionService.findActivePlan(client.getId());
        boolean enabled = plan.map(Plan::isMessagingEnabled).orElse(false);
        List<MessageResponse> messages = enabled
                ? repository.findByClientIdOrderByCreatedAtAscIdAsc(client.getId()).stream().map(this::toResponse).toList()
                : List.of();
        return new MyConversationResponse(enabled, plan.map(Plan::getName).orElse(null), messages);
    }

    @Transactional
    public MessageResponse sendMine(String content) {
        Client client = clientService.getCurrentClient();
        subscriptionService.requireFeature(client.getId(), Plan::isMessagingEnabled, NOT_INCLUDED);
        return toResponse(save(client.getId(), Role.CLIENT, content));
    }

    @Transactional
    public void markMineRead() {
        Client client = clientService.getCurrentClient();
        repository.markRead(client.getId(), Role.TRAINER, Instant.now());
    }

    public long countMyUnread() {
        Client client = clientService.getCurrentClient();
        if (!subscriptionService.hasFeature(client.getId(), Plan::isMessagingEnabled)) {
            return 0;
        }
        return repository.countByClientIdAndSenderRoleAndReadAtIsNull(client.getId(), Role.TRAINER);
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    /**
     * Inbox: every client whose plan includes messaging plus any client with history.
     * Unread first, then priority-support plans, then most recent.
     */
    public List<ConversationSummary> getConversations() {
        Map<Long, Message> lastByClient = repository.findLastMessagePerClient().stream()
                .collect(Collectors.toMap(Message::getClientId, Function.identity()));
        Map<Long, Long> unreadByClient = new HashMap<>();
        for (Object[] row : repository.countUnreadGroupedByClient(Role.CLIENT)) {
            unreadByClient.put((Long) row[0], (Long) row[1]);
        }

        Map<Long, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Function.identity()));

        return clients.values().stream()
                .map(c -> {
                    Optional<Plan> plan = subscriptionService.findActivePlan(c.getId());
                    boolean enabled = plan.map(Plan::isMessagingEnabled).orElse(false);
                    Message last = lastByClient.get(c.getId());
                    if (!enabled && last == null) {
                        return null;
                    }
                    return new ConversationSummary(
                            c.getId(), c.getFirstName() + " " + c.getLastName(), c.getPhotoBase64(),
                            plan.map(Plan::getName).orElse(null),
                            enabled,
                            plan.map(Plan::isPrioritySupport).orElse(false),
                            last != null ? last.getContent() : null,
                            last != null ? last.getSenderRole() : null,
                            last != null ? last.getCreatedAt() : null,
                            unreadByClient.getOrDefault(c.getId(), 0L));
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator
                        .comparing((ConversationSummary s) -> s.unreadCount() > 0 ? 0 : 1)
                        .thenComparing(s -> s.prioritySupport() ? 0 : 1)
                        .thenComparing(ConversationSummary::lastMessageAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ConversationResponse getConversation(Long clientId) {
        Client client = clientService.getEntityById(clientId);
        Optional<Plan> plan = subscriptionService.findActivePlan(clientId);
        return new ConversationResponse(clientId, client.getFirstName() + " " + client.getLastName(),
                plan.map(Plan::isMessagingEnabled).orElse(false),
                plan.map(Plan::getName).orElse(null),
                repository.findByClientIdOrderByCreatedAtAscIdAsc(clientId).stream().map(this::toResponse).toList());
    }

    @Transactional
    public MessageResponse sendToClient(Long clientId, String content) {
        clientService.getEntityById(clientId);
        subscriptionService.requireFeature(clientId, Plan::isMessagingEnabled, NOT_INCLUDED_TRAINER);
        return toResponse(save(clientId, Role.TRAINER, content));
    }

    @Transactional
    public void markClientConversationRead(Long clientId) {
        repository.markRead(clientId, Role.CLIENT, Instant.now());
    }

    public long countTrainerUnread() {
        return repository.countBySenderRoleAndReadAtIsNull(Role.CLIENT);
    }

    @EventListener
    @Transactional
    public void onClientDeleted(ClientDeletedEvent event) {
        repository.deleteByClientId(event.clientId());
    }

    // ── internals ───────────────────────────────────────────────────────────

    private Message save(Long clientId, Role role, String content) {
        User sender = userService.getCurrentUser();
        return repository.save(Message.builder()
                .clientId(clientId)
                .senderUserId(sender.getId())
                .senderRole(role)
                .content(content.trim())
                .build());
    }

    private MessageResponse toResponse(Message m) {
        return new MessageResponse(m.getId(), m.getClientId(), m.getSenderRole(), m.getContent(),
                m.getCreatedAt(), m.getReadAt());
    }
}
