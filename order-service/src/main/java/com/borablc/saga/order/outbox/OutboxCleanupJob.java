package com.borablc.saga.order.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class OutboxCleanupJob {
    private final OutboxRepository outboxRepository;
    private final int retentionDays;

    public OutboxCleanupJob(
            OutboxRepository outboxRepository,
            @Value("${app.outbox.retention-days:7}") int retentionDays) {
        this.outboxRepository = outboxRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanOutbox(){
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedOutboxCount = outboxRepository.deletePublishedBefore(before);
        log.info("Deleted {} outbox entries older than {} days", deletedOutboxCount, retentionDays);
    }
}
