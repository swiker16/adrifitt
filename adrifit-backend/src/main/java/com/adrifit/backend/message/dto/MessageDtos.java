package com.adrifit.backend.message.dto;

import com.adrifit.backend.user.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class MessageDtos {

    private MessageDtos() {
    }

    public record MessageResponse(
            Long id,
            Long clientId,
            Role senderRole,
            String content,
            Instant createdAt,
            Instant readAt
    ) {
    }

    public record SendMessageRequest(
            @NotBlank(message = "El mensaje no puede estar vacío")
            @Size(max = 4000, message = "El mensaje es demasiado largo")
            String content
    ) {
    }

    /** Client view of its conversation. {@code enabled} = its plan includes messaging. */
    public record MyConversationResponse(
            boolean enabled,
            String planName,
            List<MessageResponse> messages
    ) {
    }

    /** Trainer inbox row. */
    public record ConversationSummary(
            Long clientId,
            String clientName,
            String photoBase64,
            String planName,
            boolean messagingEnabled,
            boolean prioritySupport,
            String lastMessage,
            Role lastMessageFrom,
            Instant lastMessageAt,
            long unreadCount
    ) {
    }

    public record ConversationResponse(
            Long clientId,
            String clientName,
            boolean messagingEnabled,
            String planName,
            List<MessageResponse> messages
    ) {
    }

    public record UnreadCountResponse(long unread) {
    }
}
