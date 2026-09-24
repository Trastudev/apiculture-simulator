CREATE TABLE players (
    id text PRIMARY KEY,
    player_name text,
    honey_brand text,
    profile_complete boolean NOT NULL DEFAULT false,
    time_zone_id text,
    economy_balance_eur numeric(14, 2) NOT NULL DEFAULT 10000,
    economy_honey_buckets_json text,
    economy_honey_sold_kg_total numeric(14, 3) NOT NULL DEFAULT 0,
    economy_honey_sold_by_flora_json text,
    player_level integer NOT NULL DEFAULT 0,
    player_xp numeric(14, 2) NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX players_name_unique
    ON players (lower(player_name))
    WHERE player_name IS NOT NULL AND player_name <> '';

CREATE UNIQUE INDEX players_brand_unique
    ON players (lower(honey_brand))
    WHERE honey_brand IS NOT NULL AND honey_brand <> '';
