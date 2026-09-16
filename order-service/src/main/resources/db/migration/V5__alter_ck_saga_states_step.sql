ALTER TABLE saga_states
    DROP CONSTRAINT ck_saga_states_step,
    ADD CONSTRAINT ck_saga_states_step CHECK (step IN ('AWAITING_STOCK', 'AWAITING_PAYMENT', 'COMPENSATING_STOCK',
                                                       'COMPLETED', 'CANCELLED', 'CONFIRMING_STOCK'));