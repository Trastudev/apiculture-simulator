ALTER TABLE players
    ADD COLUMN IF NOT EXISTS last_production_day_key integer NOT NULL DEFAULT 0;
