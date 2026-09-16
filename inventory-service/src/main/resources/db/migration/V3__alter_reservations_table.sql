ALTER TABLE reservations
    ADD COLUMN confirmed_at   timestamptz,
    DROP CONSTRAINT ck_reservations_status,
    ADD CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONFIRMED'));