CREATE INDEX IF NOT EXISTS honey_orders_region_open_idx
  ON honey_orders (region, taken, expire_epoch_ms);
CREATE INDEX IF NOT EXISTS pollination_offers_region_open_idx
  ON pollination_offers (region, taken, expire_epoch_ms);
