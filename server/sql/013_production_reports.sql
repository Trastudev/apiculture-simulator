CREATE TABLE IF NOT EXISTS production_reports (
    owner_id text NOT NULL,
    day_key integer NOT NULL,
    summary_json text NOT NULL,
    PRIMARY KEY (owner_id, day_key)
);
