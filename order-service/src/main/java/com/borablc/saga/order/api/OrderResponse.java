package com.borablc.saga.order.api;

import com.borablc.saga.order.domain.Order;
import com.borablc.saga.order.domain.OrderStatus;

import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus());
    }
}
