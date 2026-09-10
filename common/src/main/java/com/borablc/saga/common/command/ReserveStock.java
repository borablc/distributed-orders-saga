package com.borablc.saga.common.command;

import com.borablc.saga.common.OrderItem;
import com.borablc.saga.common.SagaCommand;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReserveStock(
        UUID messageId,
        UUID orderId,
        UUID reservationId,
        List<OrderItem> lines,
        Instant occurredAt
) implements SagaCommand {
}
