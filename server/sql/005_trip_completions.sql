CREATE TABLE IF NOT EXISTS trip_completions (
    trip_id text NOT NULL,
    owner_id text,
    finished_start_epoch_ms bigint NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, finished_start_epoch_ms)
);

CREATE INDEX IF NOT EXISTS trip_completions_trip_idx
    ON trip_completions (trip_id, finished_start_epoch_ms);
