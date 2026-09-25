package com.adrifit.backend.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SendEmailRequest(
        List<Long> clientIds,
        boolean allActiveClients,
        @NotBlank(message = "El asunto es obligatorio") @Size(max = 300) String subject,
        @NotBlank(message = "El mensaje es obligatorio") @Size(max = 10000) String body
) {
}
