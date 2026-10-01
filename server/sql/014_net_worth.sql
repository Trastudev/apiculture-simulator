ALTER TABLE players
    ADD COLUMN IF NOT EXISTS net_worth_b bigint NOT NULL DEFAULT 0;

ALTER TABLE players
    ADD COLUMN IF NOT EXISTS net_worth_day_key integer NOT NULL DEFAULT 0;
