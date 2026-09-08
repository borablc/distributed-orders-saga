package com.borablc.saga.common.command;

import com.borablc.saga.common.SagaCommand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargePayment(
        UUID messageId,
        UUID orderId,
        UUID chargeId,
        String customerId,
        BigDecimal Amount,
        Instant occurredAt
) implements SagaCommand {
}
