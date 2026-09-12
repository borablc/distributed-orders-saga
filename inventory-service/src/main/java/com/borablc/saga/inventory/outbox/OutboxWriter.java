package com.borablc.saga.inventory.outbox;

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

    public void write(UUID messageId, UUID aggregateId, String type, Object payload){
        Outbox entry = new Outbox();
        entry.setId(messageId);
        entry.setAggregateId(aggregateId);
        entry.setType(type);
        entry.setPayload(objectMapper.writeValueAsString(payload));
        entry.setCreatedAt(Instant.now());
        outboxRepository.save(entry);
    }
}
