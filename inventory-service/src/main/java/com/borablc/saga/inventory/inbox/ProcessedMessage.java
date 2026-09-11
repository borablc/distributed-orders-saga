package com.borablc.saga.inventory.inbox;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedMessage {
    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
