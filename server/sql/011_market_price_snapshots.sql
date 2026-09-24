CREATE TABLE IF NOT EXISTS server_market_prices (
  day_key integer NOT NULL,
  flora_key text NOT NULL,
  price_eur numeric(14, 4) NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (day_key, flora_key)
);
CREATE INDEX IF NOT EXISTS server_market_prices_day_idx
  ON server_market_prices (day_key);
