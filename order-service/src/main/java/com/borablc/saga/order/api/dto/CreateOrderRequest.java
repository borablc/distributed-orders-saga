package com.borablc.saga.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank(message = "Customer id is required")
        String customerId,
        @NotEmpty(message = "At least one order line is required")
        @Valid
        List<OrderLineRequest> lines
) {
}
