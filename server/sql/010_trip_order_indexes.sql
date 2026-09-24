CREATE INDEX IF NOT EXISTS cargo_trips_order_idx
  ON cargo_trips (order_id);
CREATE INDEX IF NOT EXISTS honey_orders_claimed_active_idx
  ON honey_orders (claimed_by, taken, expire_epoch_ms);
