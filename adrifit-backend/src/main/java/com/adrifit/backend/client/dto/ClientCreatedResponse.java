package com.adrifit.backend.client.dto;

public record ClientCreatedResponse(
        ClientResponse client,
        String username,
        String temporaryPassword
) {
}
