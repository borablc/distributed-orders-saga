CREATE TABLE payments(
    id              uuid            NOT NULL,
    order_id        uuid            NOT NULL,
    customer_id     text            NOT NULL,
    status          text            NOT NULL,
    amount          numeric(19,2)   NOT NULL,
    created_at      timestamptz     NOT NULL,
    refunded_at     timestamptz,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT ck_payments_status CHECK (status IN ('CHARGED', 'REFUNDED')),
    CONSTRAINT ck_payments_amount CHECK (amount >= 0)
);
CREATE INDEX idx_payments_order_id ON payments(order_id);

CREATE TABLE outbox (
    id              uuid        NOT NULL,
    aggregate_id    uuid        NOT NULL,
    type            text        NOT NULL,
    payload         text        NOT NULL,
    created_at      timestamptz NOT NULL,
    published_at    timestamptz,
    attempts        integer     NOT NULL DEFAULT 0,

    CONSTRAINT pk_outbox PRIMARY KEY (id)
);
CREATE INDEX idx_outbox_unpublished
    ON outbox(created_at)
    WHERE published_at IS NULL;

CREATE TABLE processed_messages
(
    message_id uuid NOT NULL,
    processed_at timestamptz NOT NULL,

    CONSTRAINT pk_processed_messages PRIMARY KEY (message_id)
);
CREATE INDEX idx_processed_messages_processed_at
    ON processed_messages(processed_at);