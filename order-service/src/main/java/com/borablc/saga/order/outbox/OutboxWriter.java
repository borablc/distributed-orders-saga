package com.borablc.saga.order.outbox;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
public class OutboxWriter {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }
    public void writeCommand(UUID messageId, UUID aggregateId, String type, String routingKey, Object payload){
        write(
                messageId,
                aggregateId,
                type,
                OutboxDestination.RABBIT,
                routingKey,
                payload
        );
    }
    public void writeEvent(UUID messageId, UUID aggregateId, String type, Object payload){
        write(
                messageId,
                aggregateId,
                type,
                OutboxDestination.KAFKA,
                null,
                payload
        );
    }

    private void write(UUID messageId, UUID aggregateId, String type, OutboxDestination destination, String routingKey, Object payload){
        Outbox entry = new Outbox();
        entry.setId(messageId);
        entry.setAggregateId(aggregateId);
        entry.setType(type);
        entry.setPayload(objectMapper.writeValueAsString(payload));
        entry.setCreatedAt(Instant.now());
        entry.setDestination(destination);
        entry.setRoutingKey(routingKey);
        outboxRepository.save(entry);
    }
}
