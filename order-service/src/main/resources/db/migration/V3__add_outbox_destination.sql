ALTER TABLE outbox
    ADD COLUMN destination text NOT NULL,
    ADD COLUMN routing_key text,
    ADD CONSTRAINT ck_outbox_destination CHECK ( destination IN ('KAFKA', 'RABBIT')), --For better error messages
    ADD CONSTRAINT ck_outbox_routing_key CHECK ((destination = 'RABBIT' AND routing_key IS NOT NULL)
        OR (destination = 'KAFKA' AND routing_key IS NULL));