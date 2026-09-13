package com.borablc.saga.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {
    List<Outbox> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
