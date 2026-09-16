package com.borablc.saga.common;

public interface SagaFailureEvent extends SagaEvent{
    String reason();
}
