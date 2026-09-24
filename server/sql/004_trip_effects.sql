CREATE TABLE trip_effects (
    id text PRIMARY KEY,
    owner_id text,
    payload jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX trip_effects_owner_idx ON trip_effects (owner_id);
