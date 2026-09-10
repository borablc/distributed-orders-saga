package com.borablc.saga.common.event;

import com.borablc.saga.common.OrderItem;
import com.borablc.saga.common.SagaEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID messageId,
        UUID orderId,
        String customerId,
        List<OrderItem> lines,
        BigDecimal totalAmount,
        Instant occurredAt) implements SagaEvent {
}
