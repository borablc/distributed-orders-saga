package com.borablc.saga.common.event;

import com.borablc.saga.common.SagaEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefunded(
        UUID messageId,
        UUID orderId,
        UUID chargeId,
        BigDecimal amount,
        Instant occurredAt
) implements SagaEvent {
}
