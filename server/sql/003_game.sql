CREATE TABLE hex_parcels (
    id text PRIMARY KEY,
    hex_id text,
    owner_id text,
    site_id text,
    parcel_name text,
    forage_day_key integer NOT NULL DEFAULT 0,
    forage_snapshot_json text,
    is_primary boolean NOT NULL DEFAULT false,
    has_warehouse boolean NOT NULL DEFAULT false,
    warehouse_level integer NOT NULL DEFAULT 0,
    site_lat double precision NOT NULL DEFAULT 0,
    site_lng double precision NOT NULL DEFAULT 0,
    warehouse_lat double precision NOT NULL DEFAULT 0,
    warehouse_lng double precision NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX hex_parcels_owner_idx ON hex_parcels (owner_id);
CREATE INDEX hex_parcels_hex_idx ON hex_parcels (hex_id);

CREATE TABLE hex_parcel_flora (
    id text PRIMARY KEY,
    hex_id text,
    flora_key text,
    planted_at_epoch_ms bigint NOT NULL DEFAULT 0,
    ready_at_epoch_ms bigint NOT NULL DEFAULT 0,
    expire_at_day_key integer NOT NULL DEFAULT 0,
    last_maintained_year integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX hex_parcel_flora_hex_idx ON hex_parcel_flora (hex_id);

CREATE TABLE hive_daily_yields (
    id text PRIMARY KEY,
    hive_id text,
    day_key integer NOT NULL DEFAULT 0,
    kg numeric(14, 3) NOT NULL DEFAULT 0,
    worker_net_delta integer NOT NULL DEFAULT 0,
    eggs_laid integer NOT NULL DEFAULT 0,
    consumption_kg numeric(14, 3) NOT NULL DEFAULT 0,
    forage_kg numeric(14, 3) NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX hive_daily_yields_hive_idx ON hive_daily_yields (hive_id);

CREATE TABLE production_states (
    id text PRIMARY KEY,
    game_start_day_key integer NOT NULL DEFAULT 0,
    last_processed_production_day_key integer NOT NULL DEFAULT 0,
    game_real_time_anchor_epoch_ms bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE honey_orders (
    id text PRIMARY KEY,
    npc_name text,
    portrait_index integer NOT NULL DEFAULT 0,
    flora_key text,
    kg numeric(14, 3) NOT NULL DEFAULT 0,
    unit_price numeric(14, 3) NOT NULL DEFAULT 0,
    dest_hex_id text,
    dest_lat double precision NOT NULL DEFAULT 0,
    dest_lng double precision NOT NULL DEFAULT 0,
    dest_label text,
    region text,
    created_day_key integer NOT NULL DEFAULT 0,
    expire_epoch_ms bigint NOT NULL DEFAULT 0,
    taken boolean NOT NULL DEFAULT false,
    claimed_by text,
    band integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX honey_orders_claimed_idx ON honey_orders (claimed_by);

CREATE TABLE pollination_offers (
    id text PRIMARY KEY,
    hex_id text,
    flora text,
    start_doy integer NOT NULL DEFAULT 0,
    end_doy integer NOT NULL DEFAULT 0,
    band integer NOT NULL DEFAULT 0,
    region text,
    created_day_key integer NOT NULL DEFAULT 0,
    expire_epoch_ms bigint NOT NULL DEFAULT 0,
    dest_lat double precision NOT NULL DEFAULT 0,
    dest_lng double precision NOT NULL DEFAULT 0,
    npc_name text,
    portrait_index integer NOT NULL DEFAULT 0,
    taken boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE pollination_contracts (
    id text PRIMARY KEY,
    owner_id text,
    hex_id text,
    flora text,
    npc_name text,
    estate_name text,
    region text,
    climate_zone text,
    layer integer NOT NULL DEFAULT 0,
    status text,
    collected_kg numeric(14, 3) NOT NULL DEFAULT 0,
    pool_kg numeric(14, 3) NOT NULL DEFAULT 0,
    saw_peak boolean NOT NULL DEFAULT false,
    min_pct numeric(8, 3) NOT NULL DEFAULT 0,
    pay_b integer NOT NULL DEFAULT 0,
    extra_b_per_point integer NOT NULL DEFAULT 0,
    travel_cost_paid integer NOT NULL DEFAULT 0,
    accepted_day_key integer NOT NULL DEFAULT 0,
    work_days integer NOT NULL DEFAULT 0,
    due_day_key integer NOT NULL DEFAULT 0,
    start_doy integer NOT NULL DEFAULT 0,
    hive_ids_json text,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX pollination_contracts_owner_idx ON pollination_contracts (owner_id);

CREATE TABLE truck_trips (
    id text PRIMARY KEY,
    owner_id text,
    origin_lat double precision NOT NULL DEFAULT 0,
    origin_lng double precision NOT NULL DEFAULT 0,
    dest_lat double precision NOT NULL DEFAULT 0,
    dest_lng double precision NOT NULL DEFAULT 0,
    dest_hex_id text,
    dest_flora text,
    start_epoch_ms bigint NOT NULL DEFAULT 0,
    duration_ms bigint NOT NULL DEFAULT 0,
    route_polyline text,
    route_road_kinds text,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX truck_trips_owner_idx ON truck_trips (owner_id);

CREATE TABLE cargo_trips (
    id text PRIMARY KEY,
    owner_id text,
    kind text,
    phase text,
    flora_key text,
    kg numeric(14, 3) NOT NULL DEFAULT 0,
    cargo_json text,
    origin_lat double precision NOT NULL DEFAULT 0,
    origin_lng double precision NOT NULL DEFAULT 0,
    dest_lat double precision NOT NULL DEFAULT 0,
    dest_lng double precision NOT NULL DEFAULT 0,
    origin_label text,
    dest_label text,
    origin_hex_id text,
    dest_hex_id text,
    return_lat double precision NOT NULL DEFAULT 0,
    return_lng double precision NOT NULL DEFAULT 0,
    return_label text,
    return_hex_id text,
    unit_price numeric(14, 3) NOT NULL DEFAULT 0,
    npc_name text,
    hive_id text,
    start_epoch_ms bigint NOT NULL DEFAULT 0,
    duration_ms bigint NOT NULL DEFAULT 0,
    route_polyline text,
    route_road_kinds text,
    order_id text,
    leg_role text,
    vehicle_id text,
    shipment_id text,
    chain_lat double precision NOT NULL DEFAULT 0,
    chain_lng double precision NOT NULL DEFAULT 0,
    chain_label text,
    chain_hex_id text,
    price_locked integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX cargo_trips_owner_idx ON cargo_trips (owner_id);

CREATE TABLE leaderboard_entries (
    id text PRIMARY KEY,
    player_name text,
    nickname text,
    honey_brand text,
    level integer NOT NULL DEFAULT 0,
    xp numeric(14, 2) NOT NULL DEFAULT 0,
    honey_stock_kg numeric(14, 3) NOT NULL DEFAULT 0,
    total_honey_kg numeric(14, 3) NOT NULL DEFAULT 0,
    honey_sold_kg_total numeric(14, 3) NOT NULL DEFAULT 0,
    honey_sold_kg_by_flora text,
    hive_count integer NOT NULL DEFAULT 0,
    hives_iberia integer NOT NULL DEFAULT 0,
    hives_za integer NOT NULL DEFAULT 0,
    hives_mdg integer NOT NULL DEFAULT 0,
    adult_bee_count integer NOT NULL DEFAULT 0,
    map_region text,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE unique_names (
    id text PRIMARY KEY,
    kind text,
    name_key text,
    owner_id text,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX unique_names_owner_idx ON unique_names (owner_id);

CREATE TABLE global_events (
    id text PRIMARY KEY,
    status text,
    body jsonb NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE global_event_progress (
    id text PRIMARY KEY,
    body jsonb NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE event_participants (
    id text PRIMARY KEY,
    progress_id text,
    owner_id text,
    kg numeric(14, 3) NOT NULL DEFAULT 0,
    body jsonb NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX event_participants_owner_idx ON event_participants (owner_id);

CREATE TABLE event_claims (
    id text PRIMARY KEY,
    owner_id text,
    instance_id text,
    body jsonb NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX event_claims_owner_idx ON event_claims (owner_id);

CREATE TABLE market_flora_sales (
    id text PRIMARY KEY,
    day_key integer NOT NULL DEFAULT 0,
    flora_key text,
    kg_sold numeric(14, 3) NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX market_flora_sales_day_idx ON market_flora_sales (day_key);

CREATE TABLE player_stores (
    id text PRIMARY KEY,
    owner_id text,
    kind text,
    body jsonb NOT NULL DEFAULT '{}',
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX player_stores_owner_idx ON player_stores (owner_id);

CREATE TABLE locations (
    id text PRIMARY KEY,
    label text,
    lat double precision NOT NULL DEFAULT 0,
    lng double precision NOT NULL DEFAULT 0,
    flora_type text,
    virtualized boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE honey_batches (
    id text PRIMARY KEY,
    hive_id text,
    type text,
    quantity_kg numeric(14, 3) NOT NULL DEFAULT 0,
    unit_price numeric(14, 3) NOT NULL DEFAULT 0,
    created_at bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE game_events (
    id text PRIMARY KEY,
    hive_id text,
    type text,
    description text,
    timestamp_ms bigint NOT NULL DEFAULT 0,
    impact_value integer NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
