package com.adrifit.backend.notification.controller;

import com.adrifit.backend.notification.dto.PushDtos.PublicKeyResponse;
import com.adrifit.backend.notification.dto.PushDtos.PushMessage;
import com.adrifit.backend.notification.dto.PushDtos.PushStatusResponse;
import com.adrifit.backend.notification.dto.PushDtos.SubscribeRequest;
import com.adrifit.backend.notification.dto.PushDtos.UnsubscribeRequest;
import com.adrifit.backend.notification.service.PushNotificationService;
import com.adrifit.backend.user.domain.Role;
import com.adrifit.backend.user.domain.User;
import com.adrifit.backend.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Device registration for push notifications (any authenticated user). */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushNotificationService pushService;
    private final UserService userService;

    public PushController(PushNotificationService pushService, UserService userService) {
        this.pushService = pushService;
        this.userService = userService;
    }

    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> publicKey() {
        return ResponseEntity.ok(new PublicKeyResponse(pushService.publicKey()));
    }

    @GetMapping("/status")
    public ResponseEntity<PushStatusResponse> status(@RequestParam(required = false) String endpoint) {
        return ResponseEntity.ok(pushService.status(userService.getCurrentUser(), endpoint));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<PushStatusResponse> subscribe(@Valid @RequestBody SubscribeRequest request) {
        return ResponseEntity.ok(pushService.subscribe(userService.getCurrentUser(), request));
    }

    @PostMapping("/subscriptions/remove")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        pushService.unsubscribe(userService.getCurrentUser(), request.endpoint());
        return ResponseEntity.noContent().build();
    }

    /** Sends a test notification to every device of the current user. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Integer>> test() {
        User user = userService.getCurrentUser();
        String url = user.getRole() == Role.TRAINER ? "/trainer/dashboard" : "/client/dashboard";
        int delivered = pushService.sendToUsers(List.of(user.getId()),
                new PushMessage("¡Notificaciones activadas! 🎉", "Así te avisaremos de mensajes, revisiones y pagos.", url, "test"));
        return ResponseEntity.ok(Map.of("delivered", delivered));
    }
}
