package com.borablc.saga.order.saga;

record Transition(SagaStep from, String eventType) {
}
