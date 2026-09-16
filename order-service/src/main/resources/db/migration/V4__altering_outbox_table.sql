ALTER TABLE outbox
    RENAME COLUMN routing_key TO destination_key;

UPDATE outbox
SET destination_key = 'order.events'
WHERE destination_key IS NULL;

ALTER TABLE outbox
DROP CONSTRAINT ck_outbox_routing_key,
    ALTER COLUMN destination_key SET NOT NULL;