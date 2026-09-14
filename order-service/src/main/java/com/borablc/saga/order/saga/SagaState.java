package com.borablc.saga.order.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_states")
@Getter
@Setter
@NoArgsConstructor
public class SagaState {
    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false)
    private SagaStep step;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "charge_id")
    private UUID chargeId;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_transition_at", nullable = false)
    private Instant lastTransitionAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
