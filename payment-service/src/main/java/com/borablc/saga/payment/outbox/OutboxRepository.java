package com.borablc.saga.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
    List<Outbox> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.publishedAt IS NOT NULL AND o.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
