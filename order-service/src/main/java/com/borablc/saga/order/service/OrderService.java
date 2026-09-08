package com.borablc.saga.order.service;

import com.borablc.saga.order.api.CreateOrderRequest;
import com.borablc.saga.order.domain.Order;
import com.borablc.saga.order.domain.OrderLine;
import com.borablc.saga.order.domain.OrderRepository;
import com.borablc.saga.order.domain.OrderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("100.00");
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {

        Order order = new Order();
        Instant now = Instant.now();

        order.setId(UUID.randomUUID());
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        request.lines().forEach(orderLineRequest -> {
            OrderLine orderLine = new OrderLine();
            orderLine.setSku(orderLineRequest.sku());
            orderLine.setQuantity(orderLineRequest.quantity());
            order.addLine(orderLine);
        });
        order.calculateTotal(UNIT_PRICE);

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(UUID id){
        return orderRepository.findById(id);
    }
}
