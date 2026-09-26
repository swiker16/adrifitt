package com.adrifit.backend.subscription.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Trainer sets (or removes, with null) the special price of the client's current subscription. */
public record UpdatePricingRequest(
        @DecimalMin(value = "0.0", message = "El precio especial no puede ser negativo")
        BigDecimal customPrice,

        @Size(max = 200)
        String customPriceNote
) {
}
