package com.borablc.saga.order.saga;

import com.borablc.saga.common.Headers;
import com.borablc.saga.common.MessageTypes;
import com.borablc.saga.common.Queues;
import com.borablc.saga.common.SagaEvent;
import com.borablc.saga.common.Topics;
import com.borablc.saga.common.command.ReserveStock;
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
            Map.entry(new Transition(SagaStep.AWAITING_STOCK, MessageTypes.STOCK_RESERVED), SagaStep.COMPLETED)
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
        applyReply(event.orderId(), event.messageId(), messageType, Instant.now());

    }

    private void applyReply(UUID orderId, UUID messageId, String eventType, Instant now) {
        if (processedMessageRepository.markProcessed(messageId, now) == 0) {
            return;
        }

        SagaState saga = sagaStateRepository.findById(orderId).orElse(null);
        if (saga == null) {
            log.warn("Saga not found for order {}", orderId);
            return;
        }

        SagaStep next = TRANSITIONS.get(new Transition(saga.getStep(), eventType));
        if (next == null) {
            log.warn("No transition from {} on {} for order {}", saga.getStep(), eventType, orderId);
            return;
        }

        saga.setStep(next);
        saga.setLastTransitionAt(now);
        onEnter(next, saga, now);
    }

    private void onEnter(SagaStep step, SagaState saga, Instant now) {
        switch (step){
            case COMPLETED -> {
                Order order = orderRepository.findById(saga.getOrderId()).orElseThrow();
                order.setStatus(OrderStatus.CONFIRMED);
                order.setUpdatedAt(now);
            }
            case CANCELLED -> {
                Order order = orderRepository.findById(saga.getOrderId()).orElseThrow();
                order.setStatus(OrderStatus.CANCELLED);
                order.setUpdatedAt(now);
            }
            default -> { }
        }
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
    private void handleOrderCreated(OrderCreated event){
        // Check if the message has already been processed
        Instant now = Instant.now();
        if (processedMessageRepository.markProcessed(event.messageId(), now) == 0) {
            return;
        }

        // Check if the order has already been processed (duplicated)
        if (sagaStateRepository.existsById(event.orderId())){
            return;
        }

        if (!orderRepository.existsById(event.orderId())){
            log.error("Order not found for order id: {}, message_id: {}",
                    event.orderId(), event.messageId());
            return;
        }

        // Create a new saga state
        UUID reservationId = UUID.randomUUID();
        SagaState sagaState = new SagaState();
        sagaState.setOrderId(event.orderId());
        sagaState.setStep(SagaStep.AWAITING_STOCK);
        sagaState.setReservationId(reservationId);
        sagaState.setCreatedAt(now);
        sagaState.setLastTransitionAt(now);
        sagaStateRepository.save(sagaState);

        //Write the command for stock reservation to outbox
        UUID commandMessageId = UUID.randomUUID();
        ReserveStock entry = new ReserveStock(
                commandMessageId,
                event.orderId(),
                reservationId,
                event.lines(),
                now
        );

        outboxWriter.writeCommand(
                commandMessageId,
                event.orderId(),
                MessageTypes.RESERVE_STOCK,
                Queues.RK_RESERVE_STOCK,
                entry);
    }

}
