package com.borablc.saga.order.outbox;

import com.borablc.saga.common.Topics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;


    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 500)
    public void publishPendingEvents() {
        List<Outbox> pendingEvents = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pendingEvents.isEmpty()) {
            return;
        }

        Map<Outbox, CompletableFuture<SendResult<String, String>>> futures = new LinkedHashMap<>();
        pendingEvents.forEach(entry -> {
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                    Topics.ORDER_EVENTS,
                    null,
                    entry.getAggregateId().toString(),
                    entry.getPayload()
            );
            producerRecord.headers().add("message-type", entry.getType().getBytes(StandardCharsets.UTF_8));
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(producerRecord);
            futures.put(entry, future);
        });

        futures.forEach((entry, future) -> {
            try {
              future.get(10, TimeUnit.SECONDS);
              entry.setPublishedAt(Instant.now());
            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                log.warn("Error publishing entry: {}", entry.getId(), e);
            }
        });

        outboxRepository.saveAll(futures.keySet());
    }
}
