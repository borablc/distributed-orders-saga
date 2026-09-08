package com.borablc.saga.common;

import java.time.Instant;
import java.util.UUID;

public interface SagaMessage {
    UUID messageId();
    UUID orderId();
    Instant occurredAt();
}
