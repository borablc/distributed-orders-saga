package com.borablc.saga.common.command;

import com.borablc.saga.common.SagaCommand;

import java.time.Instant;
import java.util.UUID;

public record ReleaseStock(
        UUID messageId,
        UUID orderId,
        UUID reservationId,
        Instant occurredAt
) implements SagaCommand {
}
