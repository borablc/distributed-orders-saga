package com.borablc.saga.common.event;

import com.borablc.saga.common.SagaEvent;

import java.time.Instant;
import java.util.UUID;

public record StockReleased(
        UUID messageId,
        UUID orderId,
        UUID reservationId,
        Instant occurredAt
) implements SagaEvent {
}
