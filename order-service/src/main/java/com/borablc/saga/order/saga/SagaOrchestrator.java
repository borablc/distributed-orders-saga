package com.borablc.saga.order.saga;

import com.borablc.saga.common.Headers;
import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.OrderItem;
import com.borablc.saga.common.Queues;
import com.borablc.saga.common.SagaEvent;
import com.borablc.saga.common.SagaFailureEvent;
import com.borablc.saga.common.Topics;
import com.borablc.saga.common.command.ChargePayment;
import com.borablc.saga.common.command.ReleaseStock;
import com.borablc.saga.common.command.ReserveStock;
import com.borablc.saga.common.event.OrderCancelled;
import com.borablc.saga.common.event.OrderConfirmed;
import com.borablc.saga.common.event.OrderCreated;
import com.borablc.saga.common.event.PaymentCharged;
import com.borablc.saga.common.event.PaymentFailed;
import com.borablc.saga.common.event.PaymentRefunded;
import com.borablc.saga.common.event.StockReleased;
import com.borablc.saga.common.event.StockReservationFailed;
import com.borablc.saga.common.event.StockReserved;
import com.borablc.saga.order.domain.Order;
import com.borablc.saga.order.domain.OrderRepository;
import com.borablc.saga.order.domain.OrderStatus;
import com.borablc.saga.order.inbox.ProcessedMessageRepository;
import com.borablc.saga.order.outbox.OutboxWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class SagaOrchestrator {
    private final SagaStateRepository sagaStateRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OrderRepository orderRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private static final Map<Transition, SagaStep> TRANSITIONS = Map.ofEntries(
            // HAPPY PATH
            Map.entry(new Transition(SagaStep.AWAITING_STOCK, MessageTypes.STOCK_RESERVED), SagaStep.AWAITING_PAYMENT),
            Map.entry(new Transition(SagaStep.AWAITING_PAYMENT, MessageTypes.PAYMENT_CHARGED), SagaStep.COMPLETED),
            // STOCK RESERVATION FAILED -- UNHAPPY PATH
            Map.entry(new Transition(SagaStep.AWAITING_STOCK, MessageTypes.STOCK_RESERVATION_FAILED), SagaStep.CANCELLED),
            // PAYMENT FAILED -- UNHAPPY PATH
            Map.entry(new Transition(SagaStep.AWAITING_PAYMENT, MessageTypes.PAYMENT_FAILED), SagaStep.COMPENSATING_STOCK),
            Map.entry(new Transition(SagaStep.COMPENSATING_STOCK, MessageTypes.STOCK_RELEASED), SagaStep.CANCELLED)
    );

    public SagaOrchestrator(SagaStateRepository sagaStateRepository,
                            OutboxWriter outboxWriter,
                            ProcessedMessageRepository processedMessageRepository,
                            ObjectMapper objectMapper,
                            OrderRepository orderRepository) {
        this.sagaStateRepository = sagaStateRepository;
        this.outboxWriter = outboxWriter;
        this.processedMessageRepository = processedMessageRepository;
        this.objectMapper = objectMapper;
        this.orderRepository = orderRepository;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    @Transactional
    public void onOrderEvent(
            @Payload String payload,
            @Header(name = Headers.MESSAGE_TYPE, required = false) String messageType){
        switch (messageType) {
            case MessageTypes.ORDER_CREATED ->
                handleOrderCreated(objectMapper.readValue(payload, OrderCreated.class));
            case MessageTypes.ORDER_CANCELLED, MessageTypes.ORDER_CONFIRMED -> {
                // Published by this orchestrator for downstream consumers. Nothing to do here.
            }
            case null ->
                log.warn("Message type is null: {}", payload);
            default ->
                log.warn("Unknown message type: {}", messageType);
        }
    }

    @KafkaListener(topics = Topics.SAGA_REPLIES)
    @Transactional
    public void onSagaEvent(
            @Payload String payload,
            @Header(name = Headers.MESSAGE_TYPE, required = false) String messageType){
        if (messageType == null) {
            log.warn("Missing message type");
            return;
        }
        SagaEvent event = deserialize(payload, messageType);
        if (event == null) {
            log.warn("Unknown message type: {}", messageType);
            return;
        }
        applyReply(event, messageType, Instant.now());
    }

    private void applyReply(SagaEvent event, String eventType, Instant now) {
        if (processedMessageRepository.markProcessed(event.messageId(), now) == 0) {
            return;
        }

        SagaState saga = sagaStateRepository.findById(event.orderId()).orElse(null);
        if (saga == null) {
            log.warn("Saga not found for order {}", event.orderId());
            return;
        }

        SagaStep next = TRANSITIONS.get(new Transition(saga.getStep(), eventType));
        if (next == null) {
            log.warn("No transition from {} on {} for order {}", saga.getStep(), eventType, event.orderId());
            return;
        }

        if (event instanceof SagaFailureEvent failure) {
            saga.setFailureReason(failure.reason());
        }

        saga.setStep(next);
        saga.setLastTransitionAt(now);
        onEnter(next, saga, now);
    }


    private SagaEvent deserialize(String payload, String messageType) {
        switch (messageType) {
            case MessageTypes.STOCK_RESERVED -> {
                return objectMapper.readValue(payload, StockReserved.class);
            }
            case MessageTypes.STOCK_RESERVATION_FAILED -> {
                return objectMapper.readValue(payload, StockReservationFailed.class);
            }
            case MessageTypes.STOCK_RELEASED -> {
                return objectMapper.readValue(payload, StockReleased.class);
            }
            case MessageTypes.PAYMENT_CHARGED -> {
                return objectMapper.readValue(payload, PaymentCharged.class);
            }
            case MessageTypes.PAYMENT_FAILED -> {
                return objectMapper.readValue(payload, PaymentFailed.class);
            }
            case MessageTypes.PAYMENT_REFUNDED -> {
                return objectMapper.readValue(payload, PaymentRefunded.class);
            }
            default -> {
                log.warn("Unknown message type: {}", messageType);
                return null;
            }
        }
    }

    private void onEnter(SagaStep step, SagaState saga, Instant now) {
        Order order = orderRepository.findById(saga.getOrderId()).orElseThrow();
        UUID messageId = UUID.randomUUID();
        switch (step){
            case COMPLETED -> {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setUpdatedAt(now);

                //No one listens to this event.
                //It's only here if we decide to add another service that listens to this event.
                //For example, a notification service.
                OrderConfirmed orderConfirmed = new OrderConfirmed(
                        messageId,
                        saga.getOrderId(),
                        now
                );

                outboxWriter.writeEvent(
                        messageId,
                        saga.getOrderId(),
                        MessageTypes.ORDER_CONFIRMED,
                        Topics.ORDER_EVENTS,
                        orderConfirmed);
            }
            case CANCELLED -> {
                order.setStatus(OrderStatus.CANCELLED);
                order.setUpdatedAt(now);

                //No one listens to this event.
                //It's only here if we decide to add another service that listens to this event.
                //For example, a notification service.
                OrderCancelled orderCancelled = new OrderCancelled(
                        messageId,
                        saga.getOrderId(),
                        saga.getFailureReason(),
                        now
                );

                outboxWriter.writeEvent(
                        messageId,
                        saga.getOrderId(),
                        MessageTypes.ORDER_CANCELLED,
                        Topics.ORDER_EVENTS,
                        orderCancelled);
            }
            case AWAITING_PAYMENT -> {
                UUID chargeId = UUID.randomUUID();

                saga.setChargeId(chargeId);

                ChargePayment entry = new ChargePayment(
                        messageId,
                        order.getId(),
                        chargeId,
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        now
                );

                outboxWriter.writeCommand(
                        messageId,
                        order.getId(),
                        MessageTypes.CHARGE_PAYMENT,
                        Queues.RK_CHARGE_PAYMENT,
                        entry
                );
            }
            case COMPENSATING_STOCK -> {
                ReleaseStock entry = new ReleaseStock(
                        messageId,
                        order.getId(),
                        saga.getReservationId(),
                        now
                );
                outboxWriter.writeCommand(
                        messageId,
                        order.getId(),
                        MessageTypes.RELEASE_STOCK,
                        Queues.RK_RELEASE_STOCK,
                        entry
                );
            }
            case AWAITING_STOCK -> {
                UUID reservationId = UUID.randomUUID();
                saga.setReservationId(reservationId);

                List<OrderItem> items = order.getOrderLines()
                        .stream()
                        .map(line -> new OrderItem(line.getSku(), line.getQuantity()))
                        .toList();

                ReserveStock entry = new ReserveStock(
                        messageId,
                        order.getId(),
                        reservationId,
                        items,
                        now);

                outboxWriter.writeCommand(
                        messageId,
                        order.getId(),
                        MessageTypes.RESERVE_STOCK,
                        Queues.RK_RESERVE_STOCK,
                        entry);
            }
            default -> { }
        }
    }
    private void handleOrderCreated(OrderCreated event) {
        // Check if the message has already been processed
        Instant now = Instant.now();
        if (processedMessageRepository.markProcessed(event.messageId(), now) == 0) {
            return;
        }

        // Check if the order has already been processed (duplicated)
        if (sagaStateRepository.existsById(event.orderId())) {
            return;
        }

        if (!orderRepository.existsById(event.orderId())) {
            log.error("Order not found for order id: {}, message_id: {}",
                    event.orderId(), event.messageId());
            return;
        }

        // Create a new saga state
        SagaState saga = new SagaState();
        saga.setOrderId(event.orderId());
        saga.setStep(SagaStep.AWAITING_STOCK);
        saga.setCreatedAt(now);
        saga.setLastTransitionAt(now);
        saga = sagaStateRepository.save(saga);

        onEnter(SagaStep.AWAITING_STOCK, saga, now);
    }

}
