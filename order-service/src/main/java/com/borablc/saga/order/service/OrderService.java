package com.borablc.saga.order.service;

import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.OrderItem;
import com.borablc.saga.common.event.OrderCreated;
import com.borablc.saga.order.api.dto.CreateOrderRequest;
import com.borablc.saga.order.domain.Order;
import com.borablc.saga.order.domain.OrderLine;
import com.borablc.saga.order.domain.OrderRepository;
import com.borablc.saga.order.domain.OrderStatus;
import com.borablc.saga.order.outbox.Outbox;
import com.borablc.saga.order.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("100.00");
    public OrderService(OrderRepository orderRepository,
                        OutboxRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
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

        List<OrderItem> orderItems = order.getOrderLines()
                .stream()
                .map(line -> new OrderItem(line.getSku(), line.getQuantity()))
                .toList();

        UUID messageId = UUID.randomUUID();
        OrderCreated event = new OrderCreated(
                messageId,
                order.getId(),
                order.getCustomerId(),
                orderItems,
                order.getTotalAmount(),
                now
        );

        String payload = objectMapper.writeValueAsString(event);

        Outbox outboxEntry = new Outbox();
        outboxEntry.setId(messageId);
        outboxEntry.setAggregateId(order.getId());
        outboxEntry.setPayload(payload);
        outboxEntry.setType(MessageTypes.ORDER_CREATED);
        outboxEntry.setCreatedAt(now);

        outboxRepository.save(outboxEntry);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(UUID id){
        return orderRepository.findById(id);
    }
}
