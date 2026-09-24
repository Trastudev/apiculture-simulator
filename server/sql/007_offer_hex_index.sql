CREATE INDEX IF NOT EXISTS pollination_offers_hex_open_idx
  ON pollination_offers (hex_id, taken, expire_epoch_ms);
