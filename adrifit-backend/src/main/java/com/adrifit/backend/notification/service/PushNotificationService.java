package com.adrifit.backend.notification.service;

import com.adrifit.backend.client.repository.ClientRepository;
import com.adrifit.backend.common.exception.BusinessException;
import com.adrifit.backend.notification.domain.PushSubscription;
import com.adrifit.backend.notification.dto.PushDtos.PushMessage;
import com.adrifit.backend.notification.dto.PushDtos.PushStatusResponse;
import com.adrifit.backend.notification.dto.PushDtos.SubscribeRequest;
import com.adrifit.backend.notification.repository.PushSubscriptionRepository;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Web Push notifications for the PWA. Subscriptions are per device; a user can have several.
 * Payloads use the Angular service worker format so a tap opens the right screen.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);
    /** Consecutive delivery failures after which a subscription is dropped. */
    private static final int MAX_FAILURES = 5;

    private final PushSubscriptionRepository repository;
    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final WebPushSender sender;
    private final VapidKeyService vapidKeys;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public PushNotificationService(PushSubscriptionRepository repository,
                                   UserRepository userRepository,
                                   ClientRepository clientRepository,
                                   WebPushSender sender,
                                   VapidKeyService vapidKeys,
                                   ObjectMapper objectMapper,
                                   @Value("${adrifit.push.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
        this.sender = sender;
        this.vapidKeys = vapidKeys;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public String publicKey() {
        return vapidKeys.publicKey();
    }

    // ── Subscriptions ───────────────────────────────────────────────────────

    @Transactional
    public PushStatusResponse subscribe(User user, SubscribeRequest request) {
        if (!request.endpoint().startsWith("https://")) {
            throw new BusinessException("Suscripción push no válida");
        }
        PushSubscription sub = repository.findByEndpoint(request.endpoint()).orElseGet(PushSubscription::new);
        // The same browser may be used by another account after a logout: the device follows the last user.
        sub.setUserId(user.getId());
        sub.setEndpoint(request.endpoint());
        sub.setP256dh(request.keys().p256dh());
        sub.setAuth(request.keys().auth());
        sub.setUserAgent(truncate(request.userAgent(), 300));
        sub.setFailureCount(0);
        repository.save(sub);
        return status(user, request.endpoint());
    }

    @Transactional
    public void unsubscribe(User user, String endpoint) {
        repository.findByEndpoint(endpoint)
                .filter(s -> s.getUserId().equals(user.getId()))
                .ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public PushStatusResponse status(User user, String endpoint) {
        boolean thisDevice = endpoint != null && repository.findByEndpoint(endpoint)
                .map(s -> s.getUserId().equals(user.getId())).orElse(false);
        return new PushStatusResponse(enabled, thisDevice, repository.countByUserId(user.getId()));
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    public void sendToClient(Long clientId, PushMessage message) {
        clientRepository.findById(clientId).ifPresent(c -> sendToUsers(List.of(c.getUserId()), message));
    }

    public void sendToTrainers(PushMessage message) {
        sendToUsers(userRepository.findByRole(Role.TRAINER).stream().map(User::getId).toList(), message);
    }

    /** @return number of devices the message was delivered to */
    @Transactional
    public int sendToUsers(List<Long> userIds, PushMessage message) {
        if (!enabled || userIds.isEmpty()) {
            return 0;
        }
        String payload = payload(message);
        int delivered = 0;
        for (PushSubscription sub : repository.findByUserIdIn(userIds)) {
            try {
                int status = sender.send(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                if (status >= 200 && status < 300) {
                    sub.setFailureCount(0);
                    sub.setLastSuccessAt(Instant.now());
                    repository.save(sub);
                    delivered++;
                } else if (status == 404 || status == 410) {
                    // The browser revoked the subscription (uninstalled, permission removed...).
                    repository.delete(sub);
                } else {
                    registerFailure(sub, "HTTP " + status);
                }
            } catch (Exception ex) {
                registerFailure(sub, ex.getMessage());
            }
        }
        return delivered;
    }

    private void registerFailure(PushSubscription sub, String reason) {
        log.warn("Push to subscription {} failed: {}", sub.getId(), reason);
        sub.setFailureCount(sub.getFailureCount() + 1);
        if (sub.getFailureCount() >= MAX_FAILURES) {
            repository.delete(sub);
        } else {
            repository.save(sub);
        }
    }

    /** Angular service worker (ngsw) notification format: a tap opens {@code url}. */
    String payload(PushMessage message) {
        Map<String, Object> onClick = Map.of("operation", "navigateLastFocusedOrOpen", "url", message.url());
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", message.title());
        notification.put("body", message.body());
        notification.put("icon", "icons/icon-192x192.png");
        notification.put("badge", "icons/icon-96x96.png");
        notification.put("lang", "es");
        notification.put("vibrate", List.of(80, 40, 80));
        if (message.tag() != null) {
            notification.put("tag", message.tag());
            notification.put("renotify", true);
        }
        notification.put("data", Map.of("url", message.url(), "onActionClick", Map.of("default", onClick)));
        try {
            return objectMapper.writeValueAsString(Map.of("notification", notification));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String value, int max) {
        return value == null ? null : value.length() > max ? value.substring(0, max) : value;
    }
}
