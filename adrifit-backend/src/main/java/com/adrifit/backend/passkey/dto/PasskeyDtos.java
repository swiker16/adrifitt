package com.adrifit.backend.passkey.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PasskeyDtos {

    private PasskeyDtos() {
    }

    /**
     * Options for navigator.credentials.create/get. {@code publicKey} is the standard WebAuthn
     * JSON (binary fields as base64url).
     */
    public record CeremonyOptions(String requestId, JsonNode publicKey) {
    }

    /** {@code credential} is the PublicKeyCredential serialised with toJSON() (base64url fields). */
    public record FinishRegistrationRequest(
            @NotBlank String requestId,
            @NotNull JsonNode credential,
            @Size(max = 100) String name
    ) {
    }

    public record FinishAssertionRequest(@NotBlank String requestId, @NotNull JsonNode credential) {
    }

    public record PasskeyResponse(Long id, String name, boolean synced, Instant createdAt, Instant lastUsedAt) {
    }
}
