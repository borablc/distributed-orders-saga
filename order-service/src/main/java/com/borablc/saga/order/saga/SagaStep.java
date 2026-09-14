package com.borablc.saga.order.saga;

public enum SagaStep {
    AWAITING_STOCK,
    AWAITING_PAYMENT,
    COMPENSATING_STOCK,
    COMPLETED,
    CANCELLED
}
