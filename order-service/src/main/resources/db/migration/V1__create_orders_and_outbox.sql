CREATE TABLE orders (
    id            uuid           NOT NULL,
    customer_id   text           NOT NULL,
    total_amount  numeric(19, 2) NOT NULL,
    status        text           NOT NULL,
    created_at    timestamptz    NOT NULL,
    updated_at    timestamptz    NOT NULL,
    version       bigint         NOT NULL DEFAULT 0,

    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_orders_total_amount_non_negative CHECK (total_amount >= 0)
);

CREATE TABLE order_lines (
    id          bigserial   NOT NULL,
    order_id    uuid        NOT NULL,
    sku         text        NOT NULL,
    quantity    integer     NOT NULL,

    CONSTRAINT pk_order_line PRIMARY KEY (id),
    CONSTRAINT fk_order_line_order_id FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT ck_quantity_non_negative CHECK ( quantity > 0 )

);

CREATE INDEX idx_order_lines_order_id ON order_lines (order_id);

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