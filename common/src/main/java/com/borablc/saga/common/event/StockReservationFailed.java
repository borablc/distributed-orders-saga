package com.borablc.saga.common.event;

import com.borablc.saga.common.SagaFailureEvent;

import java.time.Instant;
import java.util.UUID;

public record StockReservationFailed(
        UUID messageId,
        UUID orderId,
        String reason,
        Instant occurredAt) implements SagaFailureEvent {
}
