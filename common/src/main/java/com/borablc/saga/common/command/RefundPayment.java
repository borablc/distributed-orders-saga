package com.borablc.saga.common.command;

import com.borablc.saga.common.SagaCommand;

import java.time.Instant;
import java.util.UUID;

public record RefundPayment(
        UUID messageId,
        UUID orderId,
        UUID chargeId,
        Instant occurredAt
) implements SagaCommand {
}
