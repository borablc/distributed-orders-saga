package com.borablc.saga.order.outbox;

import com.borablc.saga.common.Headers;
import com.borablc.saga.common.Queues;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
import java.util.stream.Collectors;

@Slf4j
@Component
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RabbitTemplate rabbitTemplate;


    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 500)
    public void publishPendingEntries() {
        List<Outbox> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        Map<Boolean, List<Outbox>> byDestination = pending.stream()
                .collect(Collectors.partitioningBy(e -> e.getDestination() == OutboxDestination.KAFKA));

        publishToKafka(byDestination.get(true));
        publishToRabbit(byDestination.get(false));

        outboxRepository.saveAll(pending);
    }

    private void publishToRabbit(List<Outbox> outboxes) {
        if (outboxes.isEmpty()) {
            return;
        }
        outboxes.forEach(entry -> {
            MessageProperties props = new MessageProperties();
            props.setHeader(Headers.MESSAGE_TYPE, entry.getType());
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            Message msg = new Message(entry.getPayload().getBytes(StandardCharsets.UTF_8), props);
            try {
                rabbitTemplate.send(Queues.COMMAND_EXCHANGE, entry.getDestinationKey(), msg);
                entry.setPublishedAt(Instant.now());
            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);
                log.warn("Error publishing entry: {}", entry.getId(), e);
            }
        });
    }


    private void publishToKafka(List<Outbox> outboxes) {
        if (outboxes.isEmpty()) {
            return;
        }

        Map<Outbox, CompletableFuture<SendResult<String, String>>> futures = new LinkedHashMap<>();
        outboxes.forEach(entry -> {
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(
                    entry.getDestinationKey(),
                    null,
                    entry.getAggregateId().toString(),
                    entry.getPayload()
            );
            producerRecord.headers().add(Headers.MESSAGE_TYPE, entry.getType().getBytes(StandardCharsets.UTF_8));
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
    }
}
