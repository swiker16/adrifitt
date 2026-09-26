package com.adrifit.backend.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PushDtos {

    private PushDtos() {
    }

    /** Same shape as the browser's PushSubscription.toJSON() (+ userAgent). */
    public record SubscribeRequest(
            @NotBlank @Size(max = 1000) String endpoint,
            @NotNull @Valid Keys keys,
            String userAgent
    ) {
    }

    public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {
    }

    public record PublicKeyResponse(String publicKey) {
    }

    /**
     * @param enabled    push is enabled on the server
     * @param subscribed this device (endpoint) is subscribed for the current user
     * @param devices    devices of the current user receiving notifications
     */
    public record PushStatusResponse(boolean enabled, boolean subscribed, long devices) {
    }

    /** What the user sees. {@code url} is opened when the notification is tapped. */
    public record PushMessage(String title, String body, String url, String tag) {
    }
}
