package com.borablc.saga.common.command;

import com.borablc.saga.common.SagaCommand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundPayment(
        UUID messageId,
        UUID orderId,
        UUID chargeId,
        BigDecimal amount,
        Instant occurredAt
) implements SagaCommand {
}
