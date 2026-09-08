package com.borablc.saga.common.event;

import com.borablc.saga.common.SagaEvent;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmed(
        UUID messageId,
        UUID orderId,
        Instant occurredAt
) implements SagaEvent {
}
