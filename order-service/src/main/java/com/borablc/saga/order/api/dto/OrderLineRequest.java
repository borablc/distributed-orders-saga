package com.borablc.saga.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderLineRequest(
        @NotBlank(message = "SKU is required")
        String sku,

        @Positive(message = "Quantity must be positive")
        int quantity
) {
}
