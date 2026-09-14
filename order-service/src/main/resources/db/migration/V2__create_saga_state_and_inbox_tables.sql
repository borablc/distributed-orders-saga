CREATE TABLE processed_messages
(
    message_id      uuid            NOT NULL,
    processed_at    timestamptz     NOT NULL,

    CONSTRAINT pk_processed_messages PRIMARY KEY (message_id)
);
CREATE INDEX idx_processed_messages_processed_at
    ON processed_messages(processed_at);

CREATE TABLE saga_states(
    order_id            uuid        NOT NULL,
    step                text        NOT NULL,
    reservation_id      uuid,
    charge_id           uuid,
    failure_reason      text,
    created_at          timestamptz NOT NULL,
    last_transition_at  timestamptz NOT NULL,
    version             bigint      NOT NULL DEFAULT 0,

    CONSTRAINT pk_saga_states PRIMARY KEY (order_id),
    CONSTRAINT fk_saga_states_order_id FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT ck_saga_states_step CHECK (step IN ('AWAITING_STOCK', 'AWAITING_PAYMENT', 'COMPENSATING_STOCK',
                                                   'COMPLETED', 'CANCELLED'))
);