CREATE TABLE IF NOT EXISTS honey_order_expirations (
    order_id text PRIMARY KEY,
    due_epoch_ms bigint NOT NULL,
    state text NOT NULL DEFAULT 'pending',
    processed_at timestamptz
);

CREATE INDEX IF NOT EXISTS honey_order_expirations_due_idx
    ON honey_order_expirations (due_epoch_ms)
    WHERE state = 'pending';
