ALTER TABLE players
    ADD COLUMN IF NOT EXISTS map_region text;

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS orders_delivered integer NOT NULL DEFAULT 0;

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS adult_bee_count integer NOT NULL DEFAULT 0;

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS contract_count integer NOT NULL DEFAULT 0;
