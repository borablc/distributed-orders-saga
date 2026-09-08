package com.borablc.saga.common.event;

import com.borablc.saga.common.SagaEvent;

import java.time.Instant;
import java.util.UUID;

public record OrderCancelled(
        UUID messageId,
        UUID orderId,
        String reason,
        Instant occurredAt
) implements SagaEvent {
}
