package com.adrifit.backend.message.controller;

import com.adrifit.backend.message.dto.MessageDtos.ConversationResponse;
import com.adrifit.backend.message.dto.MessageDtos.ConversationSummary;
import com.adrifit.backend.message.dto.MessageDtos.MessageResponse;
import com.adrifit.backend.message.dto.MessageDtos.MyConversationResponse;
import com.adrifit.backend.message.dto.MessageDtos.SendMessageRequest;
import com.adrifit.backend.message.dto.MessageDtos.UnreadCountResponse;
import com.adrifit.backend.message.service.MessageService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // ── Client ──────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<MyConversationResponse> getMine() {
        return ResponseEntity.ok(messageService.getMine());
    }

    @PostMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<MessageResponse> sendMine(@Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendMine(request.content()));
    }

    @PostMapping("/me/read")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> markMineRead() {
        messageService.markMineRead();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/unread-count")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<UnreadCountResponse> myUnread() {
        return ResponseEntity.ok(new UnreadCountResponse(messageService.countMyUnread()));
    }

    // ── Trainer ─────────────────────────────────────────────────────────────

    @GetMapping("/conversations")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<List<ConversationSummary>> conversations() {
        return ResponseEntity.ok(messageService.getConversations());
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<UnreadCountResponse> trainerUnread() {
        return ResponseEntity.ok(new UnreadCountResponse(messageService.countTrainerUnread()));
    }

    @GetMapping("/clients/{clientId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<ConversationResponse> conversation(@PathVariable Long clientId) {
        return ResponseEntity.ok(messageService.getConversation(clientId));
    }

    @PostMapping("/clients/{clientId}")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<MessageResponse> sendToClient(@PathVariable Long clientId,
                                                        @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messageService.sendToClient(clientId, request.content()));
    }

    @PostMapping("/clients/{clientId}/read")
    @PreAuthorize("hasRole('TRAINER')")
    public ResponseEntity<Void> markRead(@PathVariable Long clientId) {
        messageService.markClientConversationRead(clientId);
        return ResponseEntity.noContent().build();
    }
}
