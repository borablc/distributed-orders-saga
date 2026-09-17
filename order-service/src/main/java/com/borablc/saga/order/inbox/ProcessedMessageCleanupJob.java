package com.borablc.saga.order.inbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class ProcessedMessageCleanupJob {
    private final ProcessedMessageRepository processedMessageRepository;
    private final int retentionDays;
    public ProcessedMessageCleanupJob(
            ProcessedMessageRepository processedMessageRepository,
            @Value("${app.inbox.retention-days:30}") int retentionDays) {
        this.processedMessageRepository = processedMessageRepository;
        this.retentionDays = retentionDays;
    }
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanProcessedMessages() {
        Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deletedMessageCount = processedMessageRepository.deleteProcessedBefore(before);
        log.info("Deleted {} processed messages older than {} days", deletedMessageCount, retentionDays);
    }
}
