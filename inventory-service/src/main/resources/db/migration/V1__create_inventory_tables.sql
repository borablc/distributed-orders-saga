CREATE TABLE reservations (
    id            uuid           NOT NULL,
    order_id      uuid           NOT NULL,
    status        text           NOT NULL,
    created_at    timestamptz    NOT NULL,
    released_at   timestamptz,

    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE', 'RELEASED'))
);
CREATE INDEX idx_reservations_order_id ON reservations (order_id);

CREATE TABLE reservation_lines (
    id              bigserial   NOT NULL,
    reservation_id  uuid        NOT NULL,
    sku             text        NOT NULL,
    quantity        integer     NOT NULL,

    CONSTRAINT pk_reservation_lines PRIMARY KEY (id),
    CONSTRAINT uk_reservation_lines_reservation_id_sku UNIQUE (reservation_id, sku),
    CONSTRAINT fk_reservation_lines_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    CONSTRAINT ck_reservation_lines_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_reservation_lines_reservation_id ON reservation_lines (reservation_id);

CREATE TABLE stock_items(
    sku         text    NOT NULL,
    available   integer NOT NULL,
    reserved    integer NOT NULL DEFAULT 0,
    version     bigint  NOT NULL DEFAULT 0,

    CONSTRAINT pk_stock_items PRIMARY KEY (sku),
    CONSTRAINT ck_stock_items_available CHECK (available >= 0),
    CONSTRAINT ck_stock_items_reserved CHECK (reserved >= 0)
);

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